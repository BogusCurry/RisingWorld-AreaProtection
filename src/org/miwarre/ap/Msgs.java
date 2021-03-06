/****************************
	A r e a P r o t e c t i o n  -  A Rising World Java plug-in for area permissions.

	Msgs.java - Localisable user interface texts.

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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/**
 * The localisable texts for the plug-in.
 */
class Msgs
{
	//
	// The ID's of the texts
	//
	// Main menu texts
	static final int	gui_title				=  0;
	static final int	gui_showAreas			=  1;
	static final int	gui_hideAreas			=  2;
	static final int	gui_newArea				=  3;
	static final int	gui_editArea				=  4;
	static final int	gui_deleteArea			=  5;
	static final int gui_chestAccess			=  6;
	static final int	gui_areaManagers		=  7;
	static final int	gui_adminsOff			=  8;
	static final int	gui_adminsOn			=  9;
	// Cardinal points
	static final int	gui_N					= 10;
	static final int	gui_E					= 11;
	static final int	gui_S					= 12;
	static final int	gui_W					= 13;
	// New area texts
	static final int	gui_areaKeys			= 14;
	static final int	gui_areaCentreFmt		= 15;
	static final int	gui_areaSpanFmt			= 16;
	// Area properties edit
	static final int	gui_editTitle			= 17;
	static final int	gui_editName			= 18;
	static final int	gui_editPermissGeneral	= 19;
	static final int	gui_editPermissSpecific	= 20;
	static final int	gui_editPermFirst		= 21;
	static final int	gui_editPermLastArea		= 50;	// last permission applicable to an area
	static final int	gui_editPermLastUser	= 52;	// last permission applicable to a user
	// Other GUI texts
	static final int	gui_editeditPlayers		= 53;
	static final int	gui_editeditGroups		= 54;
	static final int	gui_notImplemented		= 55;
	static final	int	gui_specPermPlayersTitle= 56;
	static final	int	gui_specPermGroupsTitle	= 57;
	static final	int	gui_areaPlayerPermsTitle= 58;
	static final	int	gui_areaName			= 59;
	static final	int	gui_playerName			= 60;
	static final int	gui_editCreate			= 61;
	static final int	gui_editUpdate			= 62;
	static final int	gui_editAdd				= 63;
	static final int	gui_editDelete			= 64;
	static final int	gui_editEdit			= 65;
	static final int	gui_noOwnedArea			= 66;
	static final int	gui_customPerms			= 67;
	static final	int	gui_selectPlayer		= 68;
	static final	int	gui_selectGroup			= 69;
	static final	int	gui_selectPreset		= 70;
	static final	int	gui_topAreaHeight		= 71;
	static final	int	gui_bottomAreaHeight	= 72;
	static final	int	gui_setToDefault		= 73;
	// Other menu title
	static final int	gui_selectArea			= 74;

	private static final int	LAST_TEXT	= gui_selectArea;

	//
	// The default built-in texts, used as fall-back if no message file is found.
	//
	static String[]		msg = {
			// Main menu texts
			"Area Protection",							// 0
			"Show areas",
			"Hide areas",
			"New area",
			"Edit area",
			"Delete area",
			"Chest access (NOT impl.)",
			"Area Managers",
			"Admin priv. OFF",
			"Admin priv. ON",
			// Cardinal points
			"N",										// 10
			"E",
			"S",
			"W",
			// New area texts
			"RETURN to create, ESCAPE to abort",
			"Area Centre: %.1f%s, %.1f%s, %.1fh",
			"N/S: %d blk | E/W: %d blk | H: %d blk",
			// Area properties edit
			"Area Properties",
			"Name:",
			"Generic Area Permissions:",
			"Specific Area Permissions:",				// 20
			// Property names
			"Enter area",
			"Leave area",
			"Place blocks",
			"Destroy blocks",
			"Place constructions",
			"Remove constructions",
			"Destroy constructions",
			"Place objects",
			"Remove objects",
			"Destroy objects",							// 30
			"Place terrain",
			"Destroy terrain",
			"Place vegetation",
			"Remove vegetation",
			"Destroy vegetation",
			"Place grass",
			"Remove grass",
			"Place water",
			"Remove water",
			"Create blueprint",							// 40
			"Place blueprint",
			"Place block (creative)",
			"Place vegetation (creative)",
			"Edit terrain (creative)",
			"Put into chest",
			"Get from chest",
			"Door interaction",
			"Furnace interaction",
			"Other interaction",
			"Explosions",								// 50
			"Can add players",
			"Owner",
			// other GUI texts
			"Edit Players",
			"Edit Groups",
			"Coming soon!",
			"Players with special permissions",
			"Groups with special permissions",
			"Player/Group Permissions for Area",
			"Area Name:",
			"Player/Group Name:",						// 60
			"\n CREATE \n ",
			"\n UPDATE \n ",
			"\n ADD \n ",
			"\n DELETE \n ",
			"\n EDIT \n ",
			"[You own no area]",
			"Custom",
			"Select a player:",
			"Select a group:",
			"Select a preset:",							// 70
			"Top Height",
			"Bottom Height",
			"Set to default",
			// other menu titles
			"Select an Area"
	};

	private static final	String		MSGS_FNAME	= "/locale/messages";

	/**
	 * Initialises the texts overwriting the built-in texts with the texts for
	 * a specific locale.
	 * @param path		the plug-in path, used to locate the text files.
	 * @param locale	the locale to load texts for.
	 * @return			true: success | false: failure (built-in texts are used)
	 */
	static boolean init(String path, Locale locale)
	{
		if (locale == null)
			return false;
		String		country		= locale.getCountry();
		String		variant		= locale.getVariant();
		String		fname		= MSGS_FNAME + "_" + locale.getLanguage();
		if (country.length() > 0)	fname += "_" + country;
		if (variant.length() > 0)	fname += "_" + variant;
		fname	+= ".properties";
		Properties settings	= new Properties();
		// NOTE : use getResourcesAsStream() if the setting file is included in the distrib. .jar)
		FileInputStream in;
		try
		{
		in = new FileInputStream(path + fname);
		settings.load(in);
		in.close();
		} catch (IOException e) {
			System.out.println("** AREA PROTECTION plug-in ERROR: Property file '" + fname + "' for requested locale '"+ locale.toString() + "' not found. Defaulting to built-in 'en'");
			return false;
		}
		// Load strings from localised bundle
		for (int i = 0; i <= LAST_TEXT; i++)
			msg[i]	= settings.getProperty(String.format("%03d", i) );
		// a few strings require additional steps, to add margin around the text.
		msg[gui_editCreate]		= "\n " + msg[gui_editCreate] + " \n ";
		msg[gui_editUpdate]		= "\n " + msg[gui_editUpdate] + " \n ";
		msg[gui_editAdd]		= "\n " + msg[gui_editAdd]    + " \n ";
		msg[gui_editDelete]		= "\n " + msg[gui_editDelete] + " \n ";
		msg[gui_editEdit]		= "\n " + msg[gui_editEdit]   + " \n ";
		return true;
	}

}
