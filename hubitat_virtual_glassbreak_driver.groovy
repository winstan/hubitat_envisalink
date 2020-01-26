/***********************************************************************************************************************
*
*  A Hubitat virtual glass break (shock) detector
*
*  Copyright (C) 2020 Cybrmage
*
*  License:
*  This program is free software: you can redistribute it and/or modify it under the terms of the GNU
*  General Public License as published by the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*  implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*  for more details.
*
***********************************************************************************************************************/

metadata {
		definition (name: "Virtual GlassBreak Detector", 
			namespace: "dwb",
			author: "CybrMage", 
			importUrl: "https://raw.githubusercontent.com/omayhemo/hubitat_envisalink/master/hubitat_virtual_glassbreak_driver.groovy") {
			capability "Initialize"
			capability "ShockSensor"
			
			command "clear"
			command "detected"

			attribute   "shock", "string"
		}
}

/***********************************************************************************************************************
*   Platform Events
*/

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	unschedule()
}

def uninstalled() {
	unschedule()
}

/***********************************************************************************************************************
*   Driver Commands
*/

def clear(){
	sendEvent(name: "shock", value: "clear")
}

def detected(){
	sendEvent(name: "shock", value: "detected")
}
