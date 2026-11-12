/*
 * This file is part of the TemplateMod project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  As_One and contributors
 *
 * TemplateMod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TemplateMod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with TemplateMod.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.asone.template;

//? if >1.18.2 {
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
//?} else {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
*///?}

//? forge {
/*import net.minecraftforge.fml.common.Mod;
*///?} elif neoforge {
/*import net.neoforged.fml.common.Mod;
*///?}

//? forgelike {
/*@Mod(TemplateMod.MOD_ID)
*///?}
public class TemplateMod {
	public static final Logger LOGGER =
			//? if >1.18.2 {
			LogUtils.getLogger();
			//?} else
			//LogManager.getLogger();

	public static final String MOD_ID = "template";

	//? fabric {
	public static void fabricInit()
	{
		new TemplateMod();
	}
	//?}
	public TemplateMod() {
		LOGGER.info("Hello {}!", MOD_ID);
	}

}
