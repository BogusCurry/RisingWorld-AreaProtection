/****************************
	A r e a P r o t e c t i o n  -  A Rising World Java plug-in for area permissions.

	ListenerPlayer.java - The listener for player events

	Created by : Maurizio M. Gavioli 2017-02-25

(C) Copyright 2018 Maurizio M. Gavioli (a.k.a. Miwarre)
This Area Protection plug-in is licensed under the the terms of the GNU General
Public License as published by the Free Software Foundation, either version 3 of
the License, or (at your option) any later version.

This Area Protection plug-in is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this plug-in.  If not, see <https://www.gnu.org/licenses/>.
*****************************/

package org.miwarre.ap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.risingworld.api.events.Cancellable;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerConnectEvent;
import net.risingworld.api.events.player.PlayerEnterAreaEvent;
import net.risingworld.api.events.player.PlayerLeaveAreaEvent;
import net.risingworld.api.events.player.PlayerObjectInteractionEvent;
import net.risingworld.api.events.player.inventory.PlayerChestDropEvent;
import net.risingworld.api.events.player.inventory.PlayerChestToInventoryEvent;
import net.risingworld.api.events.player.inventory.PlayerInventoryToChestEvent;
import net.risingworld.api.events.player.world.PlayerChangeObjectStatusEvent;
import net.risingworld.api.events.player.world.PlayerCreateBlueprintEvent;
import net.risingworld.api.events.player.world.PlayerCreativePlaceBlockEvent;
import net.risingworld.api.events.player.world.PlayerCreativePlaceVegetationEvent;
import net.risingworld.api.events.player.world.PlayerCreativeTerrainEditEvent;
import net.risingworld.api.events.player.world.PlayerDestroyBlockEvent;
import net.risingworld.api.events.player.world.PlayerDestroyConstructionEvent;
import net.risingworld.api.events.player.world.PlayerDestroyObjectEvent;
import net.risingworld.api.events.player.world.PlayerDestroyTerrainEvent;
import net.risingworld.api.events.player.world.PlayerDestroyVegetationEvent;
import net.risingworld.api.events.player.world.PlayerPlaceBlockEvent;
import net.risingworld.api.events.player.world.PlayerPlaceBlueprintEvent;
import net.risingworld.api.events.player.world.PlayerPlaceConstructionEvent;
import net.risingworld.api.events.player.world.PlayerPlaceGrassEvent;
import net.risingworld.api.events.player.world.PlayerPlaceObjectEvent;
import net.risingworld.api.events.player.world.PlayerPlaceTerrainEvent;
import net.risingworld.api.events.player.world.PlayerPlaceVegetationEvent;
import net.risingworld.api.events.player.world.PlayerPlaceWaterEvent;
import net.risingworld.api.events.player.world.PlayerRemoveConstructionEvent;
import net.risingworld.api.events.player.world.PlayerRemoveGrassEvent;
import net.risingworld.api.events.player.world.PlayerRemoveObjectEvent;
import net.risingworld.api.events.player.world.PlayerRemoveVegetationEvent;
import net.risingworld.api.events.player.world.PlayerRemoveWaterEvent;
import net.risingworld.api.events.world.ExplosionEvent;
import net.risingworld.api.gui.GuiLabel;
import net.risingworld.api.gui.PivotPosition;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;
import net.risingworld.api.utils.Definitions.ObjectDefinition;
import net.risingworld.api.utils.Vector3f;

/**
 * Manages events for the plug-in.
 */
class ListenerPlayer implements Listener
{
	// Constants
	public static final		int			INFO_FONT_SIZE	= 16;
	private static final	int			APPLE_FRUIT_ID	= 46;
	private static final	int			CHERRY_FRUIT_ID	= 47;
	private static final	int			LEMON_FRUIT_ID	= 48;
	private static final	int			TOMATO_FRUIT_ID	= 100;
	private static final	int			COTTON_FRUIT_ID	= 145;
	private static final	int			CORN_FRUIT_ID	= 151;
	private static final	int			CHILI_FRUIT_ID	= 176;
	private static final ListenerPlayer ourInstance		= new ListenerPlayer();
	private static final	Set<Integer> pickables		= new HashSet<Integer>(Arrays.asList(
		APPLE_FRUIT_ID, CHERRY_FRUIT_ID, LEMON_FRUIT_ID, TOMATO_FRUIT_ID, COTTON_FRUIT_ID, CORN_FRUIT_ID, CHILI_FRUIT_ID));

	public static ListenerPlayer getInstance()		{	return ourInstance;	}

	private ListenerPlayer()						{	}

	//********************
	// EVENTS
	//********************

	/** Called by Rising World when the player connects to a world before spawning.

		@param	event	the connect event
	*/
	@EventMethod
	public void onPlayerConnect(PlayerConnectEvent event)
	{
		Player	player	= event.getPlayer();
		if (event.isNewPlayer())
			Db.resetPlayers();
		player.setAttribute(AreaProtection.key_areasShown, false);
		// The label with the names of the areas
		GuiLabel	info	= new GuiLabel("", AreaProtection.infoXPos, AreaProtection.infoYPos, false);
		info.setColor(AreaProtection.infoBkgColour);
		info.setFontColor(AreaProtection.infoFontColour);
		info.setFontSize(INFO_FONT_SIZE);
		info.setPivot(PivotPosition.BottomLeft);
		player.addGuiElement(info);
		player.setAttribute(AreaProtection.key_areasText, info);
		Db.loadPlayer(player);
	}

	/**	Called when the player issues a command ("/...") in the chat window
	
		@param event	the command event
	*/
	@EventMethod
	public void onPlayerCommand(PlayerCommandEvent event)
	{
		String	cmd		= event.getCommand().split(" ")[0];
		Player	player	= event.getPlayer();

		switch (cmd)
		{
		case "/showareas":
			Db.showAreasToPlayer(player);
			break;
		case "/hideareas":
			Db.hideAreasToPlayer(player);
			break;
		default:
			if (cmd.equals(AreaProtection.commandPrefix) )
				AreaProtection.plugin.mainGui(player);
			break;
		}
	}

	//
	// ENTER / LEAVE AREA
	//
	@EventMethod
	public void onPlayerEnterArea(PlayerEnterAreaEvent event)
	{
		int	retVal	= Db.onPlayerArea(event.getPlayer(), event.getArea(), true);
		if (retVal == AreaProtection.ERR_CANNOT_ENTER)
			event.setCancelled(true);
	}
	@EventMethod
	public void onPlayerLeaveArea(PlayerLeaveAreaEvent event)
	{
		int	retVal	= Db.onPlayerArea(event.getPlayer(), event.getArea(), false);
		if (retVal == AreaProtection.ERR_CANNOT_LEAVE)
			event.setCancelled(true);
	}

	//
	// PLACE / DESTROY BLOCK EVENTS
	//
	@EventMethod
	public void onPlayerPlaceBlock(PlayerPlaceBlockEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACEBLOCKS);
	}
	@EventMethod
	public void onPlayerDestroyBlock(PlayerDestroyBlockEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_DESTROYBLOCKS);
	}

	//
	// PLACE / REMOVE / DESTROY CONSTRUCTION EVENTS
	//
	@EventMethod
	public void onPlayerPlaceConstruction(PlayerPlaceConstructionEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACECONSTR);
	}
	@EventMethod
	public void onPlayerRemoveConstruction(PlayerRemoveConstructionEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_REMOVECONSTR);
	}
	@EventMethod
	public void onPlayerDestroyConstruction(PlayerDestroyConstructionEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_DESTROYCONSTR);
	}

	//
	// PLACE / REMOVE / DESTROY OBJECT EVENTS
	//
	@EventMethod
	public void onPlayerPlaceObject(PlayerPlaceObjectEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACEOBJECTS);
	}
	@EventMethod
	public void onPlayerRemoveObject(PlayerRemoveObjectEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_REMOVEOBJECTS);
	}
	@EventMethod
	public void onPlayerDestroyObject(PlayerDestroyObjectEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_DESTROYOBJECTS);
	}

	//
	// PLACE / DESTROY TERRAIN EVENTS
	//
	@EventMethod
	public void onPlayerPlaceTerrain(PlayerPlaceTerrainEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACETERRAIN);
	}
	@EventMethod
	public void onPlayerDestroyTerrain(PlayerDestroyTerrainEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_DESTROYTERRAIN);
	}

	//
	// PLACE / REMOVE / DESTROY VEGETATION EVENTS
	//
	@EventMethod
	public void onPlayerPlaceVegetation(PlayerPlaceVegetationEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACEVEGET);
	}
	@EventMethod
	public void onPlayerRemoveVegetation(PlayerRemoveVegetationEvent event)
	{
		// if picking up some kind fruit while leaving the plant => PERM_REMOVEVEGET
		// if picking up the plant with the fruit => PERM_DESTROYVEGET
		onCancellableEvent(event, event.getPlayer(), (pickables.contains(Integer.valueOf(event.getPlantTypeID())) ?
				AreaProtection.PERM_REMOVEVEGET : AreaProtection.PERM_DESTROYVEGET));
	}
	@EventMethod
	public void onPlayerDestroyVegetation(PlayerDestroyVegetationEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_DESTROYVEGET);
	}

	//
	// PLACE / REMOVE GRASS EVENTS
	//
	@EventMethod
	public void onPlayerPlaceGrass(PlayerPlaceGrassEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACEGRASS);
	}
	@EventMethod
	public void onPlayerRemoveGrass(PlayerRemoveGrassEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_REMOVEGRASS);
	}

	//
	// PLACE / REMOVE WATER EVENTS
	//
	@EventMethod
	public void onPlayerRemoveWater(PlayerRemoveWaterEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_REMOVEWATER);
	}
	@EventMethod
	public void onPlayerPlaceWater(PlayerPlaceWaterEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PLACEWATER);
	}

	//
	// CREATE / PLACE BLUEPRINT EVENT
	//
	@EventMethod
	public void onPlayerCreateBlueprint(PlayerCreateBlueprintEvent event)
	{
		if ( (Db.getPlayerPermissionsForBounding(event.getPlayer(), event.getBoundingInformation())
				& AreaProtection.PERM_CREATEBLUEPR) == 0)
			event.setCancelled(true);
	}
	@EventMethod
	public void onPlayerPlaceBlueprint(PlayerPlaceBlueprintEvent event)
	{
		if ( (Db.getPlayerPermissionsForBounding(event.getPlayer(), event.getBoundingInformation())
				& AreaProtection.PERM_PLACEBLUEPRINT) == 0)
			event.setCancelled(true);
	}
/*	@EventMethod
	public void onPlayerRemoveBlueprint(PlayerRemoveBlueprintEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_REMOVEBLUEPR);
	}
*/
	//
	// EVENTS IN CREATIVE MODE (F5 / F6)
	//
	@EventMethod
	public void onPlayerCreativePlaceBlock(PlayerCreativePlaceBlockEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_CREAT_PLACEBLOCKS);
	}
	@EventMethod
	public void onPlayerCreativePlaceVegetation(PlayerCreativePlaceVegetationEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_CREAT_PLACEVEGET);
	}
	@EventMethod
	public void onPlayerCreativeTerrainEdit(PlayerCreativeTerrainEditEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_CREAT_TERRAINEDIT);
	}

	//
	// CHEST EVENTS
	//
	@EventMethod
	public void onPlayerInventoryToChest(PlayerInventoryToChestEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_PUT2CHEST);
	}
	// CHEST-TO-INVENTORY and CHEST-DROP are both taking from chests
	@EventMethod
	public void onPlayerChestToInventory(PlayerChestToInventoryEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_GETFROMCHEST);
	}
	@EventMethod
	public void onPlayerChestDrop(PlayerChestDropEvent event)
	{
		onCancellableEvent(event, event.getPlayer(), AreaProtection.PERM_GETFROMCHEST);
	}

	//
	// OBJECT INTERACTION EVENTS
	//
	@EventMethod
	public void onPlayerChangeObjectStatus(PlayerChangeObjectStatusEvent event)
	{
		ObjectDefinition	def	= event.getObjectDefinition();
		long	perm	= def.isDoor() ? AreaProtection.PERM_DOORINTERACT :
						(def.isFurnace() ? AreaProtection.PERM_FURNACEINTERACT : AreaProtection.PERM_OTHERINTERACT);
		onCancellableEvent(event, event.getPlayer(), perm);
	}
	@EventMethod
	public void onPlayerObjectInteraction(PlayerObjectInteractionEvent event)
	{
		ObjectDefinition	def	= event.getObjectDefinition();
		long	perm	= def.isDoor() ? AreaProtection.PERM_DOORINTERACT :
						(def.isFurnace() ? AreaProtection.PERM_FURNACEINTERACT : 
						// Chests must be interactable if either chest access (to or from) is enabled
						(def.isChest() ? AreaProtection.PERM_OTHERINTERACT | AreaProtection.PERM_PUT2CHEST | AreaProtection.PERM_GETFROMCHEST
							: AreaProtection.PERM_OTHERINTERACT));
		onCancellableEvent(event, event.getPlayer(), perm);
	}

	//
	// EXPLOSION EVENT
	//
	@EventMethod
	public void onExplosion(ExplosionEvent event)
	{
		Player		player	= null;
		WorldItem	item	= event.getRelatedItem();
		if (item != null)
			player			= item.getRelatedPlayer();
		if (player != null &&
				(Db.getPlayerPermissionsForPoint(player, event.getPosition()) & AreaProtection.PERM_EXPLOSION) == 0)
			event.setCancelled(true);
	}

	//
	// PRIVATE METHODS
	//
	// Matches event with actual player permissions
	//
	private void onCancellableEvent(Cancellable event, Player player, long permissionFlag)
	{
		if (!AreaProtection.adminNoPriv && (Boolean)player.getAttribute(AreaProtection.key_isAdmin))	// any permission is enabled
			return;
		Long	perms	= (Long)player.getAttribute(AreaProtection.key_areaPerms);
		if (perms != null && (perms & permissionFlag) == 0)
			event.setCancelled(true);
	}
}
