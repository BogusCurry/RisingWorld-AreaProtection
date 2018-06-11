/****************************
	A r e a P r o t e c t i o n  -  A Rising World Java plug-in for area permissions.

	Db.java - The database management class

	Created by : Maurizio M. Gavioli 2017-02-25

	(C) Maurizio M. Gavioli (a.k.a. Miwarre), 2017
	Licensed under the Creative Commons by-sa 3.0 license (see http://creativecommons.org/licenses/by-sa/3.0/ for details)

*****************************/

package org.miwarre.ap;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.risingworld.api.Server;
import net.risingworld.api.database.Database;
//import net.risingworld.api.database.WorldDatabase;
import net.risingworld.api.gui.GuiLabel;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Area;
import net.risingworld.api.utils.Utils.ChunkUtils;
import net.risingworld.api.utils.Utils.GeneralUtils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import net.risingworld.api.worldelements.WorldArea;

/**
 * A data base class managing area and permission data.
 * <p>For efficiency and consistency, all methods and data are static.
 * <p>It manages persistent data storage via an SQLite DB, an in-memory
 * global cache of area data for efficiency and the player-specific area
 * permissions directly in the player attributes.
 * <p>The persistent DB is separate for each RW world.
 */
public class Db
{
	//
	// Constants
	//
	public static final	int	LIST_TYPE_PLAYER	= 1;
	public static final	int	LIST_TYPE_GROUP		= 2;
	// Globals
	private	static	TreeMap<Integer,ProtArea>	areas		= null;
			static	String[]					permGroups	= null;
			static	Database					db			= null;

	//********************
	// PROTECTED METHODS
	//********************

	/**
		Initialises and opens the DB for this plug-in. Can be run at each
		script startup without destroying existing data.
	 */
	static void init()
	{
		if (db == null)
			db = AreaProtection.plugin.getSQLiteConnection(AreaProtection.plugin.getPath()
					+ "/ap-" + AreaProtection.plugin.getWorld().getName()+".db");

		db.execute(
			"CREATE TABLE IF NOT EXISTS `areas` ("
			+ "`id`      INTEGER PRIMARY KEY, "
			+ "`from_x`  INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`from_y`  INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`from_z`  INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`to_x`    INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`to_y`    INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`to_z`    INTEGER  NOT NULL DEFAULT ( 0 ),"
			+ "`a_perm`  INTEGER NOT NULL DEFAULT ( 0 ),"
			+ "`name`    CHAR(64) NOT NULL DEFAULT ('[NoName]')"
			+ ");");
		db.execute(
			"CREATE TABLE IF NOT EXISTS `users` ("
			+ "`id`      INTEGER PRIMARY KEY,"
			+ "`area_id` INTEGER NOT NULL DEFAULT ( 0 ),"
			+ "`user_id` INTEGER NOT NULL DEFAULT ( 0 ),"
			+ "`u_perm`  INTEGER NOT NULL DEFAULT ( 0 )"
			+ ");");
		db.execute(
			"CREATE INDEX IF NOT EXISTS `user` ON `users` (`user_id`);"
		);
		db.execute(
			"CREATE UNIQUE INDEX IF NOT EXISTS `user_area` ON `users` (`user_id`, `area_id`);"
		);
		db.execute(
			"CREATE TABLE IF NOT EXISTS `groups` ("
			+ "`id`      INTEGER PRIMARY KEY,"
			+ "`area_id` INTEGER NOT NULL DEFAULT ( 0 ),"
			+ "`user_id` INTEGER NOT NULL DEFAULT ( 0 ),"
			+ "`u_perm`  INTEGER NOT NULL DEFAULT ( 0 )"
			+ ");");
		db.execute(
			"CREATE INDEX IF NOT EXISTS `group` ON `groups` (`user_id`);"
		);
		db.execute(
			"CREATE UNIQUE INDEX IF NOT EXISTS `group_area` ON `groups` (`user_id`, `area_id`);"
		);
		areas	= new TreeMap<>();
		AP3LUAImport();
		initAreas();
		initGroups();
	}
	static void deinit()
	{
		Server	server	= AreaProtection.plugin.getServer();
		for (Map.Entry<Integer,ProtArea> entry : areas.entrySet())
		{
			ProtArea	area	= entry.getValue();
			server.removeArea(area);
		}
		areas.clear();
		db.close();
		db = null;
	}

	/**
		Loads from the DB the area permissions for which a player has special permissions
		and caches them in player attributes.

 		@param	player	the target player.
	*/
	static void loadPlayer(Player player)
	{
		// the map with player-specific area permissions
		HashMap<Integer,Integer>	permAreas	= new HashMap<>();
		player.setAttribute(AreaProtection.key_areas, permAreas);
		// the map with permissions for the areas the player currently is in
		HashMap<Integer,Integer>	inAreas	= new HashMap<>();
		player.setAttribute(AreaProtection.key_inAreas, inAreas);
		// the cumulated permissions of all areas the player is currently in
		player.setAttribute(AreaProtection.key_areaPerms, AreaProtection.PERM_ALL);
		// fill the player-specific area permissions map from DB
		try (ResultSet result = db.executeQuery("SELECT `area_id`,`u_perm` FROM `users` WHERE `user_id` = "
				+ player.getDbID())) 
		{
			while (result.next())
				permAreas.put(result.getInt(1), result.getInt(2));
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
		Manages entering / exiting an area by a player

		@param	player	the player
		@param	rwArea	the area
		@param	enter	true, if the player is entering the area, false if he is leaving it
		@return	on entering an area in which the player cannot enter, ERROR_CANNOT_ENTER; otherwise SUCCESS
	*/
	static int onPlayerArea(Player player, Area rwArea, boolean enter)
	{
		ProtArea	area;
		// retrieve the permissionArea matching the given RW rwArea
		if ( (area= matchArea(rwArea)) == null)
			return AreaProtection.ERR_NOTFOUND;

		// retrieve the list of areas the player is in and set an initial all-permission for the player
//		@SuppressWarnings("unchecked")
		HashMap<Integer,Integer>	inAreas		= (HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_inAreas);
		// retrieve the list of areas the player has specific permission for
//		@SuppressWarnings("unchecked")
		HashMap<Integer,Integer>	areaPerms	= (HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_areas);
		int							cumulPerm	= AreaProtection.PERM_ALL;
		int							retVal		= AreaProtection.ERR_SUCCESS;
		// if not admin OR no admin special privilege,
		// retrieve the permissions for this player and this area.
		Integer						areaPerm	= AreaProtection.PERM_ALL;
		if (AreaProtection.adminNoPriv || !player.isAdmin())
		{
			if (areaPerms != null)
				areaPerm	= areaPerms.get(area.id);	// the player-specific permission for this area
			if (areaPerm == null)						// if no player-specific permissions...
			{
				Integer	groupPerm;						// ...get the group the player belongs to
				String	groupName	= player.getPermissionGroup();
				// convert group name into group ID
				int	groupId	= -1;
				if (groupName != null && !groupName.isEmpty())
				{
					for (int i= 0 ; i < permGroups.length; i++)
						if (permGroups[i].equals(groupName))
						{
							groupId	= i;
							break;
						}
					
				}
				// and look for group-specific permission for this area
				if (groupId > -1 && (groupPerm = area.groups.get(groupId)) != null)
					areaPerm	= groupPerm;			// if found, use them as player perms. for area
				else									// if neither group-specific permission (or no group)
					areaPerm	= area.permissions;		// ...use default area permissions
			}
		}

		// upon entering a new area
		if (enter)
		{
			// if player cannot enter this area, return so and do nothing else
			if ((areaPerm & AreaProtection.PERM_ENTER) == 0)
				return AreaProtection.ERR_CANNOT_ENTER;
			// otherwise, add this area to the list of areas the player is in
			if (inAreas != null)
				inAreas.put(area.id, areaPerm);
		}
		// upon leaving an area
		else
		{
			// if player cannot leave this area, return so and do nothing else
			if ((areaPerm & AreaProtection.PERM_LEAVE) == 0)
				return AreaProtection.ERR_CANNOT_LEAVE;
			// otherwise, remove this area from the list of areas the player is in
			if (inAreas != null)
				inAreas.remove(area.id);
		}

		// in any case, re-compute current cumulative permissions and area info text for the player.
		// The cumulative permissions are the logical AND of the permissions
		// (either default or group-specific or player-specific)
		// of all the areas the player is currently in.
		if (inAreas != null)
		{
			String	text	= "";
			for (Map.Entry<Integer,Integer> entry : inAreas.entrySet())
			{
				String	name	= areas.get(entry.getKey()).getName();	// the area name
				if (name != null)
				{
					// chain names of areas the player is in
					text	+= (text.isEmpty() ? " " : "| ");
					text	+= name + " ";
				}
				cumulPerm	&= entry.getValue();		// accumulate permissions
			}
			((GuiLabel)player.getAttribute(AreaProtection.key_areasText)).setText(text);
		}
		// if admin (and admin privileges are not limited), any permission is enabled
		if (!AreaProtection.adminNoPriv && player.isAdmin())
			cumulPerm	= AreaProtection.PERM_ALL;
		player.setAttribute(AreaProtection.key_areaPerms, cumulPerm);
		return retVal;
	}

	//********************
	//	AREA MANAGEMENT
	//********************

	/**
		Adds the area to the DB and to the local cache.

		@param	area	the area to add
		@return	An AreaProtection error code.
	*/
	static int addArea(ProtArea area)
	{
		if (area == null)
			return AreaProtection.ERR_INVALID_ARG;
		Vector3f from	= ChunkUtils.getGlobalPosition(area.getStartChunkPosition(), area.getStartBlockPosition());
		Vector3f to		= ChunkUtils.getGlobalPosition(area.getEndChunkPosition(), area.getEndBlockPosition());
		// prepare name parameter to avoid quoting issues
		try(PreparedStatement stmt	= db.getConnection().prepareStatement(
				"INSERT INTO `areas` (from_x,from_y,from_z,to_x,to_y,to_z,a_perm,name) VALUES ("
				+(int)from.x+","+(int)from.y+","+(int)from.z+","
				+(int)to.x+","+(int)to.y+","+(int)to.z+","
				+area.permissions+",?)")
		)
		{
			stmt.setString(1, area.name);
			stmt.executeUpdate();
			try (ResultSet idSet = stmt.getGeneratedKeys())
			{
				if (idSet.next())
				{
					int	newId	= idSet.getInt(1);
					area.id		= newId;
					areas.put(newId, area);
					AreaProtection.plugin.getServer().addArea(area);
				}
			}
		} catch (SQLException e)
		{
			e.printStackTrace();
			return AreaProtection.ERR_DB;
		}
		// show the new area to any player with area display turned on
		for(Player player : AreaProtection.plugin.getServer().getAllPlayers())
		{
			if ((boolean)player.getAttribute(AreaProtection.key_areasShown))
				showAreaToPlayer(player, area);
		}
		return AreaProtection.ERR_SUCCESS;
	}

	/**
	 * Deletes the given area from DB, from RW World and from player data/attributes
	 * @param area	the area to delete
	 * @return	one of the AreaProtection.ERR_ codes.
	 */
	@SuppressWarnings("unchecked")
	static int deleteArea(ProtArea area)
	{
		int		areaId	= area.id;
		// delete area data from DB
		db.executeUpdate("DELETE FROM `users` WHERE area_id="+areaId);
		db.executeUpdate("DELETE FROM `areas` WHERE id="+areaId);
		// delete RW Area
		AreaProtection.plugin.getServer().removeArea(area);
		// remove from player caches and for areas shown to players
		HashMap<Integer,Integer>	inAreas;	// the areas the player is in w/ their permissions
		HashMap<Integer,Integer>	permAreas;	// the areas for which the player has special permissions
		for(Player player : AreaProtection.plugin.getServer().getAllPlayers())
		{
			if ( (inAreas = (HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_inAreas)) != null)
				inAreas.remove(areaId);
			if ( (permAreas	= (HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_areas)) != null)
				permAreas.remove(areaId);
			if ((boolean)player.getAttribute(AreaProtection.key_areasShown))
				player.removeWorldElement(area.worldArea);
		}
		// remove from local area list
		areas.remove(area.id);
		return AreaProtection.ERR_SUCCESS;
	}

	/**
	 * Updates an existing area with the data passed in. The area to update is specified
	 * by the id field of the passed data.
	 * @param area	the new area data.
	 * @return		an AreaProtection.ERR_ error code.
	 */
	static int updateArea(ProtArea area)
	{
		if (area == null || area.id < 1)
			return AreaProtection.ERR_INVALID_ARG;
		// update the DB definition of this area
		Vector3f from	= ChunkUtils.getGlobalPosition(area.getStartChunkPosition(), area.getStartBlockPosition());
		Vector3f to		= ChunkUtils.getGlobalPosition(area.getEndChunkPosition(), area.getEndBlockPosition());
		// prepare name parameter to avoid quoting issues
		String	query	= "UPDATE `areas` SET from_x=" + (int)from.x
				+ ",from_y=" + (int)from.y
				+ ",from_z=" + (int)from.z
				+ ",to_x="   + (int)to.x
				+ ",to_y="   + (int)to.y
				+ ",to_z="   + (int)to.z
				+ ",a_perm=" + area.permissions
				+ ",name=? WHERE id=" + area.id;
		try(PreparedStatement stmt	= db.getConnection().prepareStatement(query) )
		{
			stmt.setString(1, area.name);
			stmt.execute();
		} catch (SQLException e)
		{
			e.printStackTrace();
			return AreaProtection.ERR_DB;
		}
		// update local cache too
		ProtArea	oldArea	= areas.get(area.id);	// get existing PermArea with same id
		if (oldArea != null)						// if any exists, check extent
		{
			// if extent is different, remove old RW area and add new
			// (extent of RW areas cannot be changed, once created)
			if (oldArea.getEndBlockPosition() != area.getEndBlockPosition() ||
					oldArea.getEndChunkPosition() != area.getEndChunkPosition() ||
					oldArea.getStartBlockPosition() != area.getStartBlockPosition() ||
					oldArea.getStartChunkPosition() != area.getStartChunkPosition())
			{
				AreaProtection.plugin.getServer().removeArea(oldArea);
				AreaProtection.plugin.getServer().addArea(area);
			}
			if (oldArea.getEndBlockPosition() != area.getEndBlockPosition() ||
					oldArea.getEndChunkPosition() != area.getEndChunkPosition() ||
					oldArea.getStartBlockPosition() != area.getStartBlockPosition() ||
					oldArea.getStartChunkPosition() != area.getStartChunkPosition())
			{
				AreaProtection.plugin.getServer().removeArea(oldArea);
				AreaProtection.plugin.getServer().addArea(area);
			}
			// update PermArea in cache, unless it is the same object as the area it would replace
			if (area != oldArea)
				areas.put(area.id, area);
		}
		return AreaProtection.ERR_SUCCESS;
	}

	//********************
	//	PLAYER MANAGEMENT
	//********************

	/**
	 * Adds a player/permissions pair to the player list of an area,
	 * both in the DB and in the area object.
	 * @param	area		the ares to add the player to.
	 * @param	playerId	the player id to add.
	 * @param	permissions	the permissions to add
	 * @return	an AreaProtection.ERR_ error code.
	 */
	static int addPlayerToArea(ProtArea area, int playerId, int permissions, int type)
	{
		if (area == null || area.id < 1)
			return AreaProtection.ERR_INVALID_ARG;
		// add the player/perm for this area to the DB
		// prepare name parameter to avoid quoting issues
		try(PreparedStatement stmt	= db.getConnection().prepareStatement(
				type == LIST_TYPE_PLAYER ?
						"INSERT OR REPLACE INTO `users`  (area_id,user_id,u_perm) VALUES ("+area.id+",?,"+permissions+")"
					:	"INSERT OR REPLACE INTO `groups` (area_id,user_id,u_perm) VALUES ("+area.id+",?,"+permissions+")"
				)
		)
		{
			stmt.setInt(1, playerId);
			stmt.executeUpdate();
			if (type == LIST_TYPE_PLAYER)
			{
				area.players.put(playerId, permissions);
				// if the player is connected right now, add the details to the player
				// list of areas for which he has special permissions
				Player	player	= AreaProtection.plugin.getServer().getPlayer(playerId);
				if (player != null)
				{
					// the map with player-specific area permissions
					@SuppressWarnings("unchecked")
					HashMap<Integer,Integer>	permAreas	=
							(HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_areas);
					if (permAreas != null)
						permAreas.put(area.id, permissions);
				}
			}
			else
				area.groups.put(playerId, permissions);
		} catch (SQLException e)
		{
			e.printStackTrace();
			return AreaProtection.ERR_DB;
		}
		return AreaProtection.ERR_SUCCESS;
	}

	/**
	 * Remove a player from the player list of an area,
	 * both in the DB and in the area object.
	 * @param	area		the ares to remove the player from.
	 * @param	playerId	the player id to remove.
	 * @return	an AreaProtection.ERR_ error code.
	 */
	static int removePlayerFromArea(ProtArea area, int playerId, int type)
	{
		if (area == null || area.id < 1)
			return AreaProtection.ERR_INVALID_ARG;
		// remove the player row(s) for this area from the DB
		// prepare name parameter to avoid quoting issues
		try(PreparedStatement stmt	= db.getConnection().prepareStatement(
				type == LIST_TYPE_PLAYER ?
						"DELETE FROM `users`  WHERE user_id = ? AND area_id="+area.id
					:	"DELETE FROM `groups` WHERE user_id = ? AND area_id="+area.id)
		)
		{
			stmt.setInt(1, playerId);
			stmt.executeUpdate();
			if (type == LIST_TYPE_PLAYER)
			{
				area.players.remove(playerId);
				// if the player is connected right now, remove the details from the player
				// list of areas for which he has special permissions
				Player	player	= AreaProtection.plugin.getServer().getPlayer(playerId);
				if (player != null)
				{
					// the map with player-specific area permissions
					@SuppressWarnings("unchecked")
					HashMap<Integer,Integer>	permAreas	=
							(HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_areas);
					if (permAreas != null)
						permAreas.remove(area.id);
				}
			}
			else
				area.groups.remove(playerId);
		} catch (SQLException e)
		{
			e.printStackTrace();
			return AreaProtection.ERR_DB;
		}
		return AreaProtection.ERR_SUCCESS;
	}

	/**
	 * Retrieves the special permissions of all players / groups for a given area.
	 * 
	 * @param	areaId	the id of the area for which to retrieve permissions
	 * @param	type	either LIST_TYPE_PLAYER or LIST_TYPE_GROUP
	 * @return			a Map with player name and player permissions for this area.
	 */
	static Map<Integer,Integer> getAllPlayerPermissionsForArea(int areaId, int type)
	{
		Map<Integer,Integer> areaUsers	= new TreeMap<>();
		// run the query from a separate statement, so that it can be
		// run in parallel with other queries.
		try (Statement	stmt	= db.getConnection().createStatement())
		{
			ResultSet result	=
					stmt.executeQuery(
						type == LIST_TYPE_PLAYER
						?	"SELECT user_id, u_perm FROM `users`  WHERE area_id = "+areaId
						:	"SELECT user_id, u_perm FROM `groups` WHERE area_id = "+areaId
							);
			while(result.next())
				areaUsers.put(result.getInt(1), result.getInt(2));
			result.close();
		}
		catch(SQLException e)
		{
			//on errors, do nothing and simply use what we got.
		}
		return areaUsers;
	}

	/**
	 * Returns the special permissions a player has for a specific area.
	 * <p>If the player is an admin, unconditionally returns all the permissions.
	 * <p>If the player has no special permissions for this area or is null,
	 * returns the generic permissions for this area.
	 * <p>If the area do not exists, return 0.
	 * @param	player	the player for whom to retrieve the special permissions
	 * @param	areaId	the area
	 * @return	the player permissions for the area (see details above).
	 */
	static int getPlayerPermissionsForArea(Player player, int areaId)
	{
		if (player != null)
		{
			// if player is an admin, he has all the permissions,
			// unless revoked by settings
			if (player.isAdmin() && !AreaProtection.adminNoPriv)
				return AreaProtection.PERM_ALL;
			// the map with player-specific area permissions
			@SuppressWarnings("unchecked")
			HashMap<Integer,Integer>	permAreas	=
					(HashMap<Integer, Integer>)player.getAttribute(AreaProtection.key_areas);
			if (permAreas != null)
			{
				// if the map exists, look for permissions for this specific area
				Integer	perms	= permAreas.get(areaId);
				// if specific permissions exists, return them
				if (perms != null)
					return perms;
			}
		}
		// if specific permission do not exist, or the player has no permissions map,
		// or player do not exist or is not connected, look for generic permissions for this area
		ProtArea	area	= areas.get(areaId);
		// if the area exists, return its generic permissions
		if (area != null)
			return area.permissions;
		return 0;			// if the area do not exist, return no permissions at all
	}

	/**
	 * Retrieves the areas owned by a player.
	 * <p>Any admin implicitly owns all areas unless blocked by "adminNoPriv" setting.
	 * @param player	the player to retrieve areas for.
	 * @return			a Map with area id and area data for areas owned by the player.
	 */
	static Map<Integer,ProtArea> getOwnedAreas(Player player)
	{
		// if player is an admin AND admin priviledges are not blocked, return the list of all known areas.
		if (player.isAdmin() && !AreaProtection.adminNoPriv)
			return areas;

		Map<Integer,ProtArea> ownedAreas	= new TreeMap<>();
		try(ResultSet result = db.executeQuery("SELECT area_id FROM `users` WHERE user_id = '" +
				player.getDbID() + "' AND (u_perm & ("+
				AreaProtection.PERM_OWNER + " | " + AreaProtection.PERM_ADDPLAYER + ")) != 0"))
		{
			while(result.next())
			{
				int			id		= result.getInt(1);
				ProtArea	area	= areas.get(id);
				ownedAreas.put(id, area);
			}
			result.close();
		}
		catch(SQLException e)
		{
			//on errors, do nothing and simply use what we got.
		}
		return ownedAreas;
	}

	/**
	 * Toggles on/off the display of areas for a given player.
	 * @param player	the player
	 * @return			the new value of the area display status.
	 */
	static boolean togglePlayerAreas(Player player)
	{
		boolean show	= !(boolean)player.getAttribute(AreaProtection.key_areasShown);
		player.setAttribute(AreaProtection.key_areasShown, show);
		if (show)
		{
			for (Map.Entry<Integer,ProtArea> entry : areas.entrySet())
				showAreaToPlayer(player, entry.getValue());
		}
		else
		{
			for (Map.Entry<Integer,ProtArea> entry : areas.entrySet())
			{
				ProtArea	area	= entry.getValue();
				if (area.worldArea != null)
					player.removeWorldElement(area.worldArea);
			}
		}

		return show;
	}

	//********************
	// PRIVATE HELPER METHODS
	//********************

	private static void showAreaToPlayer(Player player, ProtArea area)
	{
		if (area.worldArea == null)
		{
			area.worldArea = new WorldArea(area);
			area.worldArea.setColor(GeneralUtils.nextRandomColor(true));
			area.worldArea.setAlwaysVisible(false);
		}
		player.addWorldElement(area.worldArea);
	}

	/**
		Retrieves all the areas currently defined, add them to the server and caches them.
	*/
	private static void initAreas()
	{
		Server	server	= AreaProtection.plugin.getServer();
		try(ResultSet result = db.executeQuery("SELECT * FROM `areas`"))
		{
			while(result.next())
			{
				int		id		= result.getInt(1);
				int		fromX	= result.getInt(2);
				int		fromY	= result.getInt(3);
				int		fromZ	= result.getInt(4);
				int		toX		= result.getInt(5);
				int		toY		= result.getInt(6);
				int		toZ		= result.getInt(7);
				int		perm	= result.getInt(8);
				String	name	= result.getString(9);
				ProtArea	area	= new ProtArea(id, fromX, fromY, fromZ, toX, toY, toZ, name, perm);
				areas.put(id, area);
				server.addArea(area);
			}
			result.close();
		}
		catch(SQLException e)
		{
			//on errors, do nothing and simply use what we got.
		}
	}

	/**
		Returns the ProtArea matching the given rwArea or null if no defined ProtArea matches it.

		@param	rwArea	the Area to match
		@return	the matching ProtArea
	*/
	private static ProtArea matchArea(Area rwArea)
	{
		for (Map.Entry<Integer,ProtArea> entry : areas.entrySet())
		{
			ProtArea	area	= entry.getValue();
			if (area.equals(rwArea) )
				return area;
		}
		return null;
	}

	private static void initGroups()
	{
		String	path		= AreaProtection.plugin.getPath() + "/../../permissions/groups/";
		File	groupDir	= new File(path);
		permGroups	= groupDir.list(new FilenameFilter()
			{
				@Override
				public boolean accept(File file, String fileName)
				{
					boolean	accept	= fileName.endsWith(".permissions");
					return accept;
				}
			}
		);
		// remove the ".permissions" extension from file names
		for (int i = 0; i < permGroups.length; i++)
			permGroups[i]	= permGroups[i].substring(0, permGroups[i].length()-12);
	}

	private static void AP3LUAImport()
	{
		String	path	= AreaProtection.plugin.getPath() + "/AreaProtection";
		File	LUAdb	= new File(path + "/scriptDatabase.db");
		if (!LUAdb.isFile())
			return;

		// IMPORT AREAS

		// a map used to correlate the id each area had in the LUA db to the id it has in the new db 
		TreeMap<Integer, Integer>	oldId2NewId	= new TreeMap<>();
		// connect to the old LUA db
		Database	oldDb	= AreaProtection.plugin.getSQLiteConnection(path + "/scriptDatabase.db");
		// scan areas
		try(ResultSet result = oldDb.executeQuery("SELECT * FROM `areas`"))
		{
			while(result.next())
			{
				int			LUAid		= result.getInt(1);
				String		name		= result.getString(2);
				Vector3i	fromChunk	= new Vector3i(result.getInt(3), result.getInt(4), result.getInt(5));
				Vector3i	fromBlock	= new Vector3i(result.getInt(6), result.getInt(7), result.getInt(8));
				Vector3i	toChunk		= new Vector3i(result.getInt(9), result.getInt(10),result.getInt(11));
				Vector3i	toBlock		= new Vector3i(result.getInt(12),result.getInt(13),result.getInt(14));
//				int			playerID	= result.getInt(16);
				Vector3f	from		= ChunkUtils.getGlobalPosition(fromChunk, fromBlock);
				Vector3f	to			= ChunkUtils.getGlobalPosition(toChunk,   toBlock);
				ProtArea	area		= new ProtArea(from, to, name, AreaProtection.PERM_DEFAULT);
				addArea(area);
				oldId2NewId.put(LUAid, area.id);
			}
		}
		catch(SQLException e)
		{
			//on errors, do nothing and simply use what we got.
		}

		// IMPORT RIGHTS

		// the permissions defined in LUA groups
		Map<String, Integer>		LUAGroups	= AreaProtection.initPresets(path + "/Groups");
//		WorldDatabase				worldDb		= AreaProtection.plugin.getWorldDatabase();
		// scan rights
		try(ResultSet result = oldDb.executeQuery("SELECT * FROM `rights`"))
		{
			while(result.next())
			{
				// the data of the right
//				int		LUAid		= result.getInt(1);
				int		LUAAreaId	= result.getInt(2);
				int		playerId	= result.getInt(3);
				String	groupName	= result.getString(4);
				// convert old LUA DB area ID into our area ID
				Integer	newAreaId	= oldId2NewId.get(LUAAreaId);
				// and retrieve corresponding area
				ProtArea area		= areas.get(newAreaId);
				// if no such an area, ignore and go on with next right
				if (area == null)
					continue;
				// retrieve permissions corresponding to the group
				Integer	groupPerms	= LUAGroups.get(groupName);
				// retrieve player name from DB ID
//				String	playerName	= null;
//				try(ResultSet result2 = worldDb.executeQuery("SELECT Name FROM `Player` WHERE ID = " +
//						playerId))
//				{
//					if(result2.next())
//						playerName	= result2.getString(1);
//				}
//				catch(SQLException e)
//				{
//					playerName	= null;
//				}

				if (groupPerms != null)
					addPlayerToArea(area, playerId, groupPerms, LIST_TYPE_PLAYER);
			}
		}
		catch(SQLException e)
		{
			//on errors, do nothing and simply use what we got.
		}

		// IMPORT CHESTS

		// TODO : ???

		// now rename the file, so that it is not imported again
		oldDb.close();
		Path	oldPath	= Paths.get(path + "/scriptDatabase.db");
		try
		{
			Files.move(oldPath, oldPath.resolveSibling("scriptDatabase.db.imported"),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e)
		{
			// we did what we could...
			e.printStackTrace();
		}
	}
}