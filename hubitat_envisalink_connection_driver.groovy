/***********************************************************************************************************************
*
*  A Hubitat Driver using Telnet to connect to an Envisalink 3 or 4.
*  http://www.eyezon.com/
*
*  Copyright (C) 2018 Doug Beard
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
*  You should have received a copy of the GNU General Public License along with this program.
*  If not, see <http://www.gnu.org/licenses/>.
*
*  Name: Envisalink Connection
*  https://github.com/omayhemo/hubitat_envisalink
*
*	Special Thanks to Chuck Swchwer, Mike Maxwell and cuboy29
*	and to the entire Hubitat staff, you guys are killing it!
*	See Release Notes at the bottom
***********************************************************************************************************************/

import groovy.transform.Field

metadata {
		definition (name: "Envisalink Connection", namespace: "dwb", author: "Doug Beard") {
				capability "Initialize"
				capability "Telnet"
				capability "Alarm"
				capability "Switch"
				capability "Actuator"
				capability "Polling"
				command "sendTelnetCommand", ["String"]
				command "StatusReport"
				command "ArmAway"
				command "ArmHome"
				//command "ArmAwayZeroEntry"
				//command "SoundAlarm"
				command "Disarm"
				command "ChimeToggle"
				command "ToggleTimeStamp"
				command "poll"
				command "setUserCode", ["String", "Number", "Number"]
				command "deleteUserCode", ["Number"]
				command "configureZone", ["Number", "Number"]
				command "testParse", ["String"]

				attribute   "Status", "string"
				attribute   "Codes", "json"
				attribute   "LastUsedCodePosition", "string"
				attribute   "LastUsedCodeName", "string"
		}

		preferences {
				def PanelTypes = [:]
				PanelTypes << ["0" : "DSC"]
				PanelTypes << ["1" : "Vista"]
				input ("PanelType", "enum", title: "Panel Type", options: PanelTypes, defaultValue: 0)
				input("ip", "text", title: "IP Address",  required: true)
				input("passwd", "text", title: "Password", required: true)
				input("masterCode", "text", title: "Master Code", required: true)
				input("installerCode", "text", title: "Installer Code", description: "Installer Code is required if you wish to program the panel from this driver", required: false)
				def pollRate = [:]
				pollRate << ["0" : "Disabled"]
				pollRate << ["1" : "Poll every minute"]
				pollRate << ["5" : "Poll every 5 minutes"]
				pollRate << ["10" : "Poll every 10 minutes"]
				pollRate << ["15" : "Poll every 15 minutes"]
				pollRate << ["30" : "Poll every 30 minutes (not recommended)"]
				input ("poll_Rate", "enum", title: "Device Poll Rate", options: pollRate, defaultValue: 0)

				if (installerCode) {
						def delayOptions = [:]
						delayOptions << ["030" : "30 seconds"]
						delayOptions << ["045" : "45 seconds"]
						delayOptions << ["060" : "60 seconds"]
						delayOptions << ["090" : "90 seconds"]
						delayOptions << ["120" : "120 seconds"]
						input ("entry_delay1", "enum", title: "Entry Delay 1", options: delayOptions, defaultValue: 060)
						input ("entry_delay2", "enum", title: "Entry Delay 2", options: delayOptions, defaultValue: 060)
						input ("exit_delay", "enum", title: "Exit Delay 2", options: delayOptions, defaultValue: 060)
				}
		}
	
}

/***********************************************************************************************************************
*   Platform Events
*/

def installed() {
	ifDebug("installed...")
    initialize()
}

def updated() {
	ifDebug("updated...")
    ifDebug("Configuring IP: ${ip}, Code: ${masterCode}, Password: ${passwd}")
	initialize()
	unschedule()
	switch(poll_Rate) {
		case "0" :
		    ifDebug("Envisalink Polling is Disabled")
			break
		case "1" :
			runEvery1Minute(poll)
		    ifDebug("Poll Rate set at 1 minute")
			break
		case "5" :
			runEvery5Minutes(poll)
		    ifDebug("Poll Rate set at 5 minutes")
			break
		case "10" :
			runEvery10Minutes(poll)
		    ifDebug("Poll Rate set at 10 minutes")
			break
		case "15" :
			runEvery15Minutes(poll)
		    ifDebug("Poll Rate set at 15 minutes")
			break
		case "30" :
			runEvery30Minutes(poll)
		    ifDebug("Poll Rate set at 30 minutes")
			break
	}

    if (installerCode) {
        setDelays(entry_delay1, entry_delay2, exit_delay)
    }
}

def initialize() {
   runIn(5, "telnetConnection")
   state.programmingMode = ""
   if (PanelType == null){
       PanelType = 0
   }
}

def uninstalled() {
	telnetClose()
	removeChildDevices(getChildDevices())
}

/***********************************************************************************************************************
*   Driver Commands
*/

def ArmAway(){
	ifDebug("armAway()")
    state.armState = "arming_away"
    composeArmAway()
}

def ArmHome(){
 	ifDebug("armHome()")
    composeArmHome()
}

def ArmAwayZeroEntry(){
 	ifDebug("ArmAwayZeroEntry()")
    composeZeroEntryDelayArm()
}

def both(){
    ifDebug("both()")
 	siren()
    strobe()
}

def configureZone(zonePosition, zoneDefinition){
    ifDebug("configureZone ${zonePosition} ${zoneDefinition}")
    composeZoneConfiguration(zonePosition, zoneDefinition)
}

def ChimeToggle(){
	ifDebug("ChimeToggle()")
    composeChimeToggle()
}

def deleteUserCode(position){
    ifDebug("deleteUserCode ${position}")
    composeDeleteUserCode(position)
}

def Disarm(){
 	ifDebug("Disarm()")
    composeDisarm()
}

def on(){
    ifDebug("On")
    ArmAway()
}

def off(){
 	ifDebug("Off")
    Disarm()
}

def poll() {
	ifDebug("Polling...")
	composePoll()
}

def SoundAlarm(){
 	ifDebug("Sound Alarm : NOT IMPLEMENTED")
}

def siren(){
	ifDebug("Siren : NOT IMPLEMENTED")
}

def StatusReport(){
	ifDebug("StatusReport")
    composeStatusReport()
}

def strobe(){
 	ifDebug("Stobe : NOT IMPLEMENTED")
}

def setUserCode(name, position, code){
    ifDebug("setUserCode ${name} ${position} ${code}")
    composeSetUserCode(name, position, code)   
}

def setDelays(entry, entry2, exit){
    ifDebug("setDelays ${entry} ${entry2} ${exit}")
    composeSetDelays(entry, entry2, exit)
}

def TogleTimeStamp(){
    ifDebug("Toggle Time Stamp")
    composeTimeStampToggle()
}

// def testParse(position){
//     parseUser("11111102")
// }

/***********************************************************************************************************************
*   End Points
*/

def createZone(zoneInfo){
    log.info "Creating ${zoneInfo.zoneName} with deviceNetworkId = ${zoneInfo.deviceNetworkId} of type: ${zoneInfo.zoneType} for panel type: " + PanelType
    def newDevice
    if (zoneInfo.zoneType == "0")
    {
    	addChildDevice("hubitat", "Virtual Contact Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneName])
      newDevice = getChildDevice(zoneInfo.deviceNetworkId)
      if(PanelType as int == 1) {
      	// Vista does not report motion sensor inactive... make it automatic
       	log.info("Setting autoInactive for Virtual Contact Sensor for Vista Panel")
      	newDevice.updateSetting("autoInactive",[type:"enum", value:"60"])
      }

    } else {
     	addChildDevice("hubitat", "Virtual Motion Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneName])
        newDevice = getChildDevice(zoneInfo.deviceNetworkId)
        if(PanelType as int == 0) {
        	newDevice.updateSetting("autoInactive",[type:"enum", value:disabled])
        } else {
        	// Vista does not report motion sensor inactive... make it automatic
        	log.info("Setting autoInactive for Virtual Motion Sensor for Vista Panel")
        	newDevice.updateSetting("autoInactive",[type:"enum", value:"60"])
        }
    }

}

def removeZone(zoneInfo){
    log.info "Removing ${zoneInfo.zoneName} with deviceNetworkId = ${zoneInfo.deviceNetworkId}"
    deleteChildDevice(zoneInfo.deviceNetworkId)
}

/***********************************************************************************************************************
*   Compositions
*/
private composeArmAway(){
    ifDebug("composeArmAway")
    def message = tpiCommands["ArmAway"]
    if(PanelType as int == 1) {
    	message = masterCode + "2"
    }
	  sendTelnetCommand(message)
}

private composeArmHome(){
    ifDebug("composeArmHome")
    state.armState = "arming_home"
    def message = tpiCommands["ArmHome"]
    if(PanelType as int == 1) {
    	message = masterCode + "3"
    }
    sendTelnetCommand(message)
}

private composeChimeToggle(){
    ifDebug("composeChimeToggle")
    def message = tpiCommands["ToggleChime"]
    if(PanelType as int == 1) { message = masterCode + "9" }
    sendTelnetCommand(message)
}

private composeEnterInstallerMode(){
    ifDebug("composeEnterInstallerMode")
    composeKeyStrokes("*8" + installerCode)
}

private composeExitInstallerMode(){
    ifDebug("composeExitInstallerMode")
    composeKeyStrokes("##")
}

private composeDisarm(){
    ifDebug("composeDisarm")
    if (PanelType as int == 0){
        def message = tpiCommands["Disarm"] + masterCode
        sendTelnetCommand(message)
    } else {
    	def message = masterCode + "1"
         sendTelnetCommand(message)
        // work around disarm bug in Vista panels
         sendTelnetCommand(message)
    }
}

private composeDeleteUserCode(position){
    ifDebug("composeDeleteUserCode ${position}")
     def codePosition = position.toString()
    codePosition = codePosition.padLeft(2, "0")
    ifDebug("padded code position ${codePosition}")
    state.programmingMode = DELETEUSERCODEINITIALIZE
    state.newCodePosition = codePosition
    composeKeyStrokes("*5" + masterCode)
}

private composeInstallerCode(){
    ifDebug("composeInstallerCode")
    if(PanelType as int == 0) {
	    def sendTelnetCommand = tpiCommands["CodeSend"] + installerCode
	    ifDebug(sendTelnetCommand)
	    sendTelnetCommand(sendTelnetCommand)
	  } else {
			ifDebug("Not supported by Vista TPI")
	  }
}

private composeKeyStrokes(data){
    ifDebug("composeKeyStrokes: ${data}")
    if(PanelType as int == 0) {
	    sendMessage = tpiCommands["SendKeyStroke"]
	  } else { sendMessage = "" }
    sendProgrammingMessage(sendMessage + data)
}

private composeMasterCode(){
    ifDebug("composeMasterCode")
    if(PanelType as int == 0) {
	    def sendTelnetCommand = tpiCommands["CodeSend"] + masterCode
	    ifDebug(sendTelnetCommand)
	    sendTelnetCommand(sendTelnetCommand)
	  } else {
			ifDebug("Not supported by Vista TPI")
	  }
}

private composePoll(){
    ifDebug("composePoll")
    if(PanelType as int == 0) {
    	def message = tpiCommands["Poll"]
    	sendTelnetCommand(message)
    } else {
    	sendTelnetCommand("^0,\$")
    }
}

private composeStatusReport(){
    ifDebug("composeStatusReport")
    if(PanelType as int == 0) {
	    sendTelnetCommand(tpiCommands["StatusReport"])
    } else {
    	ifDebug("Not supported by Vista TPI")
    }
}

private composeSetUserCode(name, position, code){
    ifDebug("composeSetUserCode")
    if(PanelType as int == 0) {
	    state.programmingMode = SETUSERCODE
  	  ifDebug("Current Codes: ${device.currentValue("Codes")}")
  	  if (!device.currentValue("Codes")){
    	    def tempMap = [:]
      	  def tempJson = new groovy.json.JsonBuilder(tempMap)
      	   sendEvent(name:"Codes", value: tempMap, displayed:true, isStateChange: true)
    	}
    	def codePosition = position.toString()
    	codePosition = codePosition.padLeft(2, "0")
    	def newCode = code.toString()
    	newCode = newCode.padLeft(4, "0")
    	ifDebug("padded: ${codePosition} ${newCode}")

    	state.newCode = newCode
    	state.newCodePosition = codePosition
    	state.newName = name
    	state.programmingMode = SETUSERCODEINITIALIZE
    	composeKeyStrokes("*5" + masterCode)
    } else {
    	ifDebug("Not supported by Vista TPI")
    }
}

private composeSetDelays(entry, entry2, exit){
    ifDebug("composeSetDelays")
    ifDebug("Not Yet Implemented")
    // composeEnterInstallerMode()
    // pauseExecution(7000)

    // def result = composeKeyStrokes("005")
    // pauseExecution(5000)

    // composeKeyStrokes("01")
    // pauseExecution(5000)

    // composeKeyStrokes(entry.toString())
    // pauseExecution(5000)

    // composeKeyStrokes(entry2.toString())
    // pauseExecution(5000)

    // composeKeyStrokes(exit.toString())
    // pauseExecution(5000)

    // composeExitInstallerMode()
}

private composeTimeStampToggle(){
    if(PanelType as int == 0) {
    	ifDebug("composeTimeStampToggle")

	    def message
	    if (state.timeStampOn)
	    {
	    	message = tpiCommands["TimeStampOn"]
	    }
	    else{
	     	message = tpiCommands["TimeStampOff"]
	    }
	    sendTelnetCommand(message)
    } else {
    	ifDebug("Not supported by Vista TPI")
    }
}

private composeZoneConfiguration(zonePosition, zoneDefinition){
    ifDebug("composeZoneConfiguration ${zonePosition} ${zoneDefinition}")
    ifDebug("Not Yet Implemented")
}

private composeZeroEntryDelayArm(){
    ifDebug("composeZeroEntryDelayArm")
    def message = tpiCommands["ArmAwayZeroEntry"]
    if(PanelType as int == 1) {
    	// equivilent to Arm Stay Instant
    	message = masterCode + "7"
    }
    sendTelnetCommand(message)
}

private parseVistaFlags(flagBitMask, flagBeep, alphaDisplay){
	def flags = [
		ARMED_STAY: (flagBitMask & 0x8000) && true,
		LOW_BATTERY: (flagBitMask & 0x4000) && true,
		FIRE_ALARM: (flagBitMask & 0x2000) && true,
		READY: (flagBitMask & 0x1000) && true,
		UNUSED_1: (flagBitMask & 0x0800) && true,
		UNUSED_2: (flagBitMask & 0x0400) && true,
		CHECK_ZONE: (flagBitMask & 0x0200) && true,
		ALARM_FIRE: (flagBitMask & 0x0100) && true,
		ENTRY_DELAY_OFF: (flagBitMask & 0x0080) && true,
		PROGRAMMING_MODE: (flagBitMask & 0x0040) && true,
		CHIME_MODE: (flagBitMask & 0x0020) && true,
		BYPASSED: (flagBitMask & 0x0010) && true,
		AC_PRESENT: (flagBitMask & 0x0008) && true,
		ARMED_AWAY: (flagBitMask & 0x0004) && true,
		ALARM_MEMORY: (flagBitMask & 0x0002) && true,
		ALARM: (flagBitMask & 0x0001) && true,
		PERIMETER_ONLY: (flagBeep & 0x10) && true,
		BEEP: (flagBeep & 0x0F) && true,
		EXIT_DELAY_ACTIVE: (alphaDisplay.compareToIgnoreCase("you may exit    ") == 0) && true,
		ENTRY_DELAY_ACTIVE: (alphaDisplay.compareToIgnoreCase("or alarm occurs ") == 0) && true
	]

	if (alphaDisplay.compareToIgnoreCase("alarm canceled  ") == 0) { flags.ALARM_MEMORY = true }
	if (flagBeep == 5) { flags.EXIT_DELAY_ACTIVE = true }

	return flags	

}

/***********************************************************************************************************************
*   Telnet
*/

def parse(String message) {
    ifDebug("Parsing Incoming message: [" + message + "]")
	//ifDebug("Response: ${tpiResponses[message.take(3) as int]}")
    if(PanelType as int == 0) {
        message = preProcessMessage(message)
        if(tpiResponses[message.take(3) as int] == COMMANDACCEPTED) {
            if (state.programmingMode == SETUSERCODESEND){
                setUserCodeSend()
            }

            if (state.programmingMode == SETUSERCODECOMPLETE){
                setUserCodeComplete()
            }

            if (state.programmingMode == DELETEUSERCODE){
                deleteUserCodeSend()
            }
            
            if (state.programmingMode == DELETEUSERCOMPLETE){
                deleteUserCodeComplete()
            }
        } 


        if(tpiResponses[message.take(3) as int] == SYSTEMERROR) {
            systemError(message)
        }

        if(tpiResponses[message.take(3) as int] == KEYPADLEDSTATE) {
            keypadLedState(message.substring(3,message.size()))
        }

        if(tpiResponses[message.take(3) as int] == CODEREQUIRED) {
            composeMasterCode()
        }

        if(tpiResponses[message.take(3) as int] == MASTERCODEREQUIRED) {
            composeMasterCode()
        }

        if(tpiResponses[message.take(3) as int] == INSTALLERSCODEREQUIRED) {
            composeInstallerCode()
        }

        if(tpiResponses[message.take(3) as int] == ZONEOPEN) {
            zoneOpen(message)
        }

        if(tpiResponses[message.take(3) as int] == ZONERESTORED) {
            zoneClosed(message)
        }

        if(tpiResponses[message.take(3) as int] == PARTITIONREADY) {
            partitionReady()
        }

        if(tpiResponses[message.take(3) as int] == PARTITIONNOTREADY) {
            partitionNotReady()
        }

        if(tpiResponses[message.take(3) as int] == PARTITIONNOTREADYFORCEARMINGENABLED) {
            partitionReadyForForcedArmEnabled()
        }

        if(tpiResponses[message.take(3) as int] == PARTITIONINALARM) {
            partitionAlarm()
        }

        if(tpiResponses[message.take(3) as int] == PARTITIONDISARMED) {
            partitionDisarmed()
        }

        if(tpiResponses[message.take(3) as int] == EXITDELAY) {
            exitDelay()
        }

        if(tpiResponses[message.take(3) as int] == ENTRYDELAY) {
            entryDelay()
        }

        if(tpiResponses[message.take(3) as int] == KEYPADLOCKOUT) {
            keypadLockout()
        }

        if(tpiResponses[message.take(3) as int] == LOGININTERACTION) {
            if(tpiResponses[message.take(4) as int] == LOGINPROMPT) {
                loginPrompt()
            }

            if(tpiResponses[message.take(4) as int] == PASSWORDINCORRECT) {
                logError(PASSWORDINCORRECT)
            }

            if(tpiResponses[message.take(4) as int] == LOGINSUCCESSFUL) {
                ifDebug(LOGINSUCCESSFUL)
            }

            if(tpiResponses[message.take(3) as int] == LOGINTIMEOUT) {
                logError(LOGINTIMEOUT)
            }

        }

        if(tpiResponses[message.take(3) as int] == PARTITIONARMEDSTATE) {

            if(tpiResponses[message.take(5) as int] == PARTITIONARMEDAWAY) {
                partitionArmedAway()
            }

            if(tpiResponses[message.take(5) as int] == PARTITIONARMEDHOME) {
                state.armState = "armed_home"
                partitionArmedHome()
            }
        }

        if(tpiResponses[message.take(3) as int] == USEROPENING){
            parseUser(message)
        }

        if(tpiResponses[message.take(3) as int] == USERCLOSING){
            parseUser(message)
        }

        if(tpiResponses[message.take(3) as int] == SPECIALCLOSING){
        }

        if(tpiResponses[message.take(3) as int] == SPECIALOPENING){
        }
  } else {
    ifDebug("Panel Type: VISTA")
    ifDebug("Processing VISTA message: ")
    if(message.take(6) == "Login:") {
	  ifDebug("Received Login: request")
      loginPrompt()
    }

    if(message.take(6) == "FAILED") {
      logError(PASSWORDINCORRECT)
    }

    if(message.take(2) == "OK") {
      ifDebug(LOGINSUCCESSFUL)
    }

    if(message.take(2) == "Timed Out!") {
      logError(LOGINTIMEOUT)
    }


    if(message.take(3) == "%00") {
	    ifDebug("Received %00 (Keypad Update) message")
	    // [%00,01,1C08,08,00,****DISARMED****  Ready to Arm  $]
	    def mPartition = message[4..5]
	    def mFlags = Integer.parseInt(message[7..10],16)
	    def mUserOrZone = message[12..13]
	    def mChime = message[15..16]
	    mChime = mChime.isInteger() ? (mChime as int) : 0
	    def mDisplay = message[18..49]
	    def vistaFlags = parseVistaFlags(mFlags,mChime,mDisplay)
	    ifDebug("Vista FLAGS = " + vistaFlags.inspect())
	    
	    if ( vistaFlags.READY ) { partitionReady() }
	    if ( vistaFlags.ARMED_AWAY ) { partitionArmedAway() }
	    if ( vistaFlags.ARMED_STAY ) { partitionArmedHome() }
	    if ( vistaFlags.ALARM || vistaFlags.ALARM_FIRE || vistaFlags.FIRE_ALARM ) { partitionAlarm() }
	    if ( mDisplay.startsWith("FAULT") ) {
					ifDebug("     Keypad Update: Zone " + mUserOrZone + " Tripped!")
					zoneOpen("000" + mUserOrZone.toString())
	    }
	    if ( mDisplay.startsWith("BYPAS") ) {
					ifDebug("     Keypad Update: Zone " + mUserOrZone + " Bypassed!")
	    }
	    if ( mDisplay.startsWith("CHECK") ) {
					ifDebug("     Keypad Update: Zone " + mUserOrZone + " CHECK notification!")
	    }
	    if ( mDisplay.startsWith("TRBL") ) {
					ifDebug("     Keypad Update: Zone " + mUserOrZone + " TROUBLE notification!")
	    }
	    
    }
    if(message.take(3) == "%01") {
	    ifDebug("Received %01 (Zone State Change) message")
	    def ZoneState = Integer.parseInt(message[18..19] + message[16..17] + message[14..15] + message[12..13] + message[10..11] + message[8..9] + message[6..7] + message[4..5],16)
	    ifDebug("         Zone State Change: Zone String [" + ZoneState + "]")
			for (i = 1; i <65; i++) {
				if ( ZoneState & (2**(i-1)) ) {
					ifDebug("     Zone State Change: Zone " + i + " Tripped!")
					zoneOpen("000" + i.toString())
				} else {
					zoneClosed("000" + i.toString())
				}
			}
    }
    if(message.take(3) == "%02") {
	    ifDebug("Received %02 (Partition State Change) message")
 			def p1Status = Integer.parseInt(message[4..5])
 			def p2Status = Integer.parseInt(message[6..7])
 			def p3Status = Integer.parseInt(message[8..9])
 
			def partitionStates = [
				0:"Partition is not Used/Doesn't Exist",
				1:"Ready",
				2:"Ready to Arm (Zones are Bypasses)",
				3:"Not Ready",
				4:"Armed in Stay Mode",
				5:"Armed in Away Mode",
				6:"Armed Maximum (Armed in Stay Mode - Zero Entry Delay)",
				7:"Exit Delay",
				8:"Partition is in Alarm",
				9:"Alarm Has Occurred (Alarm in Memory)"
			]
			ifDebug("        Partition 1: " + partitionStates[p1Status])
			ifDebug("        Partition 2: " + partitionStates[p2Status])
			ifDebug("        Partition 3: " + partitionStates[p3Status])
			if (p1Status == 1) {
				partitionReady()
			}
			if (p1Status == 2) {
				partitionReady()
			}
			if (p1Status == 3) {
				partitionNotReady()
			}
			if (p1Status == 4) {
				partitionArmedHome()
			}
			if (p1Status == 5) {
				partitionArmedAway()
			}
			if (p1Status == 6) {
				partitionArmedHome()
			}
			if (p1Status == 7) {
				exitDelay()
			}
			if (p1Status == 8) {
				partitionAlarm()
			}
			
 
    }
    if(message.take(3) == "%03") {
	    ifDebug("Received %03 (Realtime CID Event) message")
	    def Qualifier = message[4]
	    def CID = message[5..7]
	    def Partition = message[8..9]
	    def ZoneOrUser = message[10..12]
	    ifDebug("  Q: [" + Qualifier +"] CID: [" + CID + "] Partition: [" + Partition + "] Zone/User: [" + ZoneOrUser + "]")
    }
    if(message.take(3) == "%FF") {
	    ifDebug("Received %FF (Zone Timer Dump) message")
    }



  }
}

private sendTelnetLogin(){
	log.info("sendTelnetLogin: ${passwd}")
    def cmdToSend =  "${passwd}"
    if(PanelType as int == 0) {
        cmdToSend =  tpiCommands["Login"] + "${passwd}"
        def cmdArray = cmdToSend.toCharArray()
        def cmdSum = 0
        cmdArray.each { cmdSum += (int)it }
        def chkSumStr = DataType.pack(cmdSum, 0x08)
        if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
        cmdToSend += chkSumStr
    }
  cmdToSend = cmdToSend + "\r\n"
  sendHubCommand(new hubitat.device.HubAction(cmdToSend, hubitat.device.Protocol.TELNET))
}

private sendTelnetCommand(String s) {
    if(PanelType as int == 0) {
        s = generateChksum(s)
    }
    ifDebug("sendTelnetCommand $s")
	return new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET)
}

private sendProgrammingMessage(String s){
    s = generateChksum(s)
    ifDebug("sendProgrammingMessage: ${s}")
    def hubaction = new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET) 
    sendHubCommand(hubaction);
}

def telnetConnection(){
 	telnetClose()
	pauseExecution(5000)
	try {
		//open telnet connection
		telnetConnect([termChars:[13,10]], ip, 4025, null, null)
		runOnce(new Date(now() + 10000), StatusReport)
	} catch(e) {
		logError("initialize error: ${e.message}")
	}
}

def telnetStatus(String status){
	logError("telnetStatus- error: ${status}")
	if (status != "receive error: Stream is closed"){
		getReTry(true)
		logError("Telnet connection dropped...")

	} else {
		logError("Telnet is restarting...")
	}
	runOnce(new Date(now() + 10000), telnetConnection)
}

/***********************************************************************************************************************
*   Helpers
*/

private checkTimeStamp(message){
    if (message =~ timeStampPattern){
        //ifDebug("Time Stamp Found")
        	state.timeStampOn = true;
        	message = message.replaceAll(timeStampPattern, "")
        	ifDebug("Time Stamp Remove ${message}")
        }
        else{
            state.timeStampOn = false;
        	//ifDebug("Time Stamp Not Found")
        }
    return message
}

private deleteUserCodeSend(){
    ifDebug("deleteUserCodeSend")
    state.programmingMode = DELETEUSERCOMPLETE
    pauseExecution(3000)
    composeKeyStrokes("#")
}

private deleteUserCodeComplete(){
    ifDebug("deleteUserCodeComplete")
    state.programmingMode = ""
    def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
    assert storedCodes instanceof Map

    ifDebug("storedCodes: ${storedCodes}")
    def selectedCode = storedCodes[state.newCodePosition]

    ifDebug("Selected Code: ${selectedCode}")
    storedCodes.remove(state.newCodePosition.toString())

    def json = new groovy.json.JsonBuilder(storedCodes)
    sendEvent(name:"Codes", value: json, displayed:true, isStateChange: true)
    state.newCode = ""
    state.newCodePosition = ""
    state.name = ""

}

private entryDelay(){
	ifDebug("entryDelay")
    sendEvent(name:"Status", value: ENTRYDELAY)
	state.armState = "intrusion"
	parent.speakEntryDelay()
}

private exitDelay(){
    ifDebug("exitDelay")
    sendEvent(name:"Status", value: EXITDELAY)
    parent.speakExitDelay()
}

private generateChksum(String cmdToSend){
    ifDebug("generateChksum")
		def cmdArray = cmdToSend.toCharArray()
        ifDebug("cmdArray: ${cmdArray}")
        def cmdSum = 0
        cmdArray.each { cmdSum += (int)it }
        def chkSumStr = DataType.pack(cmdSum, 0x08)
        if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
        cmdToSend += chkSumStr
    	cmdToSend
}

private getReTry(Boolean inc){
	def reTry = (state.reTryCount ?: 0).toInteger()
	if (inc) reTry++
	state.reTryCount = reTry
	return reTry
}

private ifDebug(msg){
	parent.ifDebug('Connection Driver: ' + msg)
}

private loginPrompt(){
    log.info("loginPrompt")
    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
    ifDebug("Connection to Envisalink established")
    state.reTryCount = 0
    sendTelnetLogin()
    ifDebug(LOGINPROMPT)
}

private keypadLockout(){
    ifDebug("keypadLockout")
    sendEvent(name:"Status", value: KEYPADLOCKOUT)
}

private keypadLedState(ledState){
    ifDebug("keypadLedState ${ledState}")
     if (ledState == "82" && state.programmingMode == SETUSERCODEINITIALIZE){
        ifDebug("${KEYPADLEDSTATE} ${state.programmingMode}")
        state.programmingMode = SETUSERCODESEND
        composeKeyStrokes(state.newCodePosition + state.newCode)
    }
    
    if (ledState == "82" && state.programmingMode == DELETEUSERCODEINITIALIZE){
        ifDebug("${KEYPADLEDSTATE} ${state.programmingMode}")
        state.programmingMode = DELETEUSERCODE
        composeKeyStrokes(state.newCodePosition + "*")
    }

    def ledBinary = Integer.toBinaryString(hubitat.helper.HexUtils.hexStringToInt(ledState))
    def paddedBinary = ledBinary.padLeft(8, "0")
    ifDebug("${paddedBinary}")

    if (paddedBinary.substring(7,8) == "0"){
        ifDebug("Partition Ready LED Off")        
        
    }

    if (paddedBinary.substring(7,8) == "1"){
        ifDebug("Partition Ready LED On")        
    }

    
}

private logError(msg){
	parent.logError('Connection Driver: ' + msg)
}

private partitionReady(){
    ifDebug("partitionReady")
    sendEvent(name:"Status", value: PARTITIONREADY)
    sendEvent(name: "switch", value: "off")
    state.armState = "disarmed"
    state.newCode = ""
    state.newCodePosition = ""
    state.newName = ""
    state.programmingMode = ""
}

private partitionNotReady(){
    ifDebug("partitionNotReady")
    sendEvent(name:"Status", value: PARTITIONNOTREADY)
}

private partitionReadyForForcedArmEnabled(){
    ifDebug("partitionReadyForForcedArmEnabled")
    sendEvent(name:"Status", value: PARTITIONNOTREADYFORCEARMINGENABLED)
}

private partitionAlarm(){
    ifDebug("partitionAlarm")
    sendEvent(name:"Status", value: PARTITIONINALARM)
    state.armState = "alarming"
	parent.speakAlarm()
}

private partitionDisarmed(){
    ifDebug("partitionDisarmed")
    sendEvent(name:"Status", value: PARTITIONDISARMED)
    sendEvent(name: "switch", value: "off")
    if (state.armState != "disarmed"){
		ifDebug("disarming")
		state.armState = "disarmed"
		parent.unlockIt()
		parent.switchItDisarmed()
		parent.speakDisarmed()

		if (location.hsmStatus != "disarmed")
		{
			sendLocationEvent(name: "hsmSetArm", value: "disarm")
		}
	}
}

private partitionArmedAway(){
    ifDebug("partitionArmedAway")
    sendEvent(name:"Status", value: PARTITIONARMEDAWAY)
    sendEvent(name: "switch", value: "on")
    if (state.armState.contains("home")){
        systemArmedHome()
    }else {
        systemArmed()
    }
}

private partitionArmedHome(){
    ifDebug("partitionArmedHome")
    sendEvent(name:"Status", value: PARTITIONARMEDHOME)
    sendEvent(name: "switch", value: "on")
    if (state.armState.contains("home")){
        systemArmedHome()
    }else {
        systemArmed()
    }
}

private parseUser(message){
    ifDebug("parseUser")
    def length = message.size()
    def userPosition = message.substring(6,length)
    ifDebug("${USEROPENING} - ${userPosition}" )

    sendEvent(name:"LastUsedCodePosition", value: userPosition)

    def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map

    ifDebug("storedCodes: ${storedCodes}")
    def selectedCode = storedCodes[userPosition.toString()]
    assert selectedCode instanceof Map

    ifDebug("Selected Code: ${selectedCode}")
    ifDebug("Selected Code: ${selectedCode.name}")

    if (selectedCode?.name){
        sendEvent(name:"LastUsedCodeName", value: selectedCode.name)
    }

    return userPosition
}

private preProcessMessage(message){
    //ifDebug("Preprocessing Message")
 	message = checkTimeStamp(message)
    //strip checksum
    message = message.take(message.size() - 2)
    //ifDebug("Stripping Checksum: ${message}")
    return message
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

private setUserCodeSend(){
    ifDebug("setUserCodeSend")
    state.programmingMode = SETUSERCODECOMPLETE
    ifDebug("COMMAND ACCEPTED")
    pauseExecution(3000)
    composeKeyStrokes("#")
}

private setUserCodeComplete(){
    ifDebug("setUserCodeSend")
    state.programmingMode = ""
    def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
    assert storedCodes instanceof Map
    def newCodeMap = [name: (state.newName), code: (state.newCode.toString())]
    storedCodes.put((state.newCodePosition.toString()), (newCodeMap))
    ifDebug("storedCodes: ${storedCodes}")
    def json = new groovy.json.JsonBuilder(storedCodes)
    sendEvent(name:"Codes", value: json, displayed:true, isStateChange: true)
    state.newCode = ""
    state.newCodePosition = ""
    state.name = ""
}

private systemArmed(){
	if (state.armState != "armed_away"){
		ifDebug("Armed Away")
		state.armState = "armed_away"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus == "disarmed")
		{
			sendLocationEvent(name: "hsmSetArm", value: "armAway")
		}
	}
}

private systemArmedHome(){
	if (state.armState != "armed_home"){
		ifDebug("Armed Home")
		state.armState = "armed_home"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus == "disarmed")
		{
			sendLocationEvent(name: "hsmSetArm", value: "armHome")
		}
	}
}

private systemError(message){
    def substringCount = message.size() - 3
    message = message.substring(4,message.size()).replaceAll('0', '') as int
    //message = message.substring(substringCount).take(3).replaceAll('0', '')
    logError("System Error: ${message} - ${errorCodes[(message)]}")

    if (errorCodes[(message)] == "Receive Buffer Overrun"){
        composeKeyStrokes("#")
    }
}

private zoneOpen(message){
    def zoneDevice
    def substringCount = message.size() - 3
    zoneDevice = getChildDevice("${device.deviceNetworkId}_${message.substring(substringCount).take(3)}")
    if (zoneDevice == null){
        zoneDevice = getChildDevice("${device.deviceNetworkId}_M_${message.substring(substringCount).take(3)}")
    }
    log.debug zoneDevice
    if (zoneDevice){
        if (zoneDevice.capabilities.find { item -> item.name.startsWith('Contact')}){
            ifDebug("Contact Open")
            zoneDevice.open()
         }else {
            ifDebug("Motion Active")
            zoneDevice.active()
        }
    }

}

private zoneClosed(message){
    def zoneDevice
    def substringCount = message.size() - 3
    zoneDevice = getChildDevice("${device.deviceNetworkId}_${message.substring(substringCount).take(3)}")
    if (zoneDevice == null){
        zoneDevice = getChildDevice("${device.deviceNetworkId}_M_${message.substring(substringCount).take(3)}")
    }
    if (zoneDevice){
    	ifDebug(zoneDevice)
        if (zoneDevice.capabilities.find { item -> item.name.startsWith('Contact')}){
	     	ifDebug("Contact Closed")
    		zoneDevice.close()
        }else {
            ifDebug("Motion Inactive")
            zoneDevice.inactive()
        }
    }
}


/***********************************************************************************************************************
*   Variables
*/

@Field String timeStampPattern = ~/^\d{2}:\d{2}:\d{2} /

@Field final Map 	errorCodes = [
    0: 	"No Error",
    1: 	"Receive Buffer Overrun",
    2: 	"Receive Buffer Overflow",
    3: 	"Transmit Buffer Overflow",
    10: "Keybus Transmit Buffer Overrun",
    11: "Keybus Transmit Time Timeout",
    12: "Keybus Transmit Mode Timeout",
    13: "Keybus Transmit Keystring Timeout",
    14: "Keybus Interface Not Functioning (the TPI cannot communicate with the security system)",
    15: "Keybus Busy (Attempting to Disarm or Arm with user code)",
    16: "Keybus Busy – Lockout (The panel is currently in Keypad Lockout – too many disarm attempts)",
    17: "Keybus Busy – Installers Mode (Panel is in installers mode, most functions are unavailable)",
    18: "Keybus Busy – General Busy (The requested partition is busy)",
    20: "API Command Syntax Error",
    21: "API Command Partition Error (Requested Partition is out of bounds)",
    22: "API Command Not Supported",
    23: "API System Not Armed (sent in response to a disarm command)",
    24: "API System Not Ready to Arm (system is either not-secure, in exit-delay, or already armed",
    25: "API Command Invalid Length 26 API User Code not Required",
    26: "API User Code not Required",
    27: "API Invalid Characters in Command (no alpha characters are allowed except for checksum"
]

@Field static final String COMMANDACCEPTED = "Command Accepted"
@Field static final String KEYPADLEDSTATE = "Keypad LED State"
@Field static final String PROGRAMMINGON = "Keypad LED State ON"
@Field static final String PROGRAMMINGOFF = "Keypad LED State OFF"
@Field static final String COMMANDERROR = "Command Error"
@Field static final String SYSTEMERROR = "System Error"
@Field static final String LOGININTERACTION = "Login Interaction"
@Field static final String LEDFLASHSTATE = "Keypad LED FLASH state"
@Field static final String TIMEDATEBROADCAST = "Time - Date Broadcast"
@Field static final String RINGDETECTED = "Ring Detected"
@Field static final String INDOORTEMPBROADCAST = "Indoor Temp Broadcast"
@Field static final String OUTDOORTEMPBROADCAST = "Outdoor Temperature Broadcast"
@Field static final String ZONEALARM = "Zone Alarm"
@Field static final String ZONE1ALARM = "Zone 1 Alarm"
@Field static final String ZONE2ALARM = "Zone 2 Alarm"
@Field static final String ZONE3ALARM = "Zone 3 Alarm"
@Field static final String ZONE4ALARM = "Zone 4 Alarm"
@Field static final String ZONE5ALARM = "Zone 5 Alarm"
@Field static final String ZONE6ALARM = "Zone 6 Alarm"
@Field static final String ZONE7ALARM = "Zone 7 Alarm"
@Field static final String ZONE8ALARM = "Zone 8 Alarm"
@Field static final String ZONEALARMRESTORE = "Zone Alarm Restored"
@Field static final String ZONE1ALARMRESTORE = "Zone 1 Alarm Restored"
@Field static final String ZONE2ALARMRESTORE = "Zone 2 Alarm Restored"
@Field static final String ZONE3ALARMRESTORE = "Zone 3 Alarm Restored"
@Field static final String ZONE4ALARMRESTORE = "Zone 4 Alarm Restored"
@Field static final String ZONE5ALARMRESTORE = "Zone 5 Alarm Restored"
@Field static final String ZONE6ALARMRESTORE = "Zone 6 Alarm Restored"
@Field static final String ZONE7ALARMRESTORE = "Zone 7 Alarm Restored"
@Field static final String ZONE8ALARMRESTORE = "Zone 8 Alarm Restored"
@Field static final String ZONETAMPER = "Zone Tamper"
@Field static final String ZONETAMPERRESTORE = "Zone Tamper RestoreD"
@Field static final String ZONEFAULT = "Zone Fault"
@Field static final String ZONEFAULTRESTORED = "Zone Fault RestoreD"
@Field static final String ZONEOPEN = "Zone Open"
@Field static final String ZONERESTORED = "Zone Restored"
@Field static final String TIMERDUMP = "Envisalink Zone Timer Dump"
@Field static final String BYPASSEDZONEBITFIELDDUMP = "Bypassed Zones Bitfield Dump"
@Field static final String DURESSALARM = "Duress Alarm"
@Field static final String FKEYALARM = "Fire Key Alarm"
@Field static final String FKEYRESTORED = "Fire Key Restored"
@Field static final String AKEYALARM = "Aux Key Alarm"
@Field static final String AKEYRESTORED = "Aux Key Restored"
@Field static final String PKEYALARM = "Panic Key Alarm"
@Field static final String PKEYRESTORED = "Panic Key Restored"
@Field static final String TWOWIRESMOKEAUXALARM = "2-Wire Smoke Aux Alarm"
@Field static final String TWOWIRESMOKEAUXRESTORED = "2-Wire Smoke Aux Restored"
@Field static final String PARTITIONREADY = "Ready"
@Field static final String PARTITIONNOTREADY = "Not Ready"
@Field static final String PARTITIONARMEDSTATE = "Armed State"
@Field static final String PARTITIONARMEDAWAY = "Armed Away"
@Field static final String PARTITIONARMEDHOME = "Armed Home"
@Field static final String PARTITIONNOTREADYFORCEARMINGENABLED = "Partition Ready - Force Arming Enabled"
@Field static final String PARTITIONINALARM = "In Alarm"
@Field static final String PARTITIONDISARMED = "Disarmed"
@Field static final String EXITDELAY = "Exit Delay in Progress"
@Field static final String ENTRYDELAY = "Entry Delay in Progress"
@Field static final String KEYPADLOCKOUT = "Keypad Lock-out"
@Field static final String PARTITIONFAILEDTOARM = "Partition Failed to Arm"
@Field static final String PFMOUTPUT = "PFM Output is in Progress"
@Field static final String CHIMEENABLED = "Chime Enabled"
@Field static final String CHIMEDISABLED = "Chime Disabled"
@Field static final String INVALIDACCESSCODE = "Invalid Access Code"
@Field static final String FUNCTIONNOTAVAILABLE = "Function Not Available"
@Field static final String FAILURETOARM = "Failure to Arm"
@Field static final String PARTITIONISBUSY = "Partition is busy"
@Field static final String SYSTEMARMINGPROGRESS = "System Arming Progress"
@Field static final String SYSTEMININSTALLERSMODE = "System in Installers Mode"
@Field static final String USERCLOSING = "User Closing"
@Field static final String SPECIALCLOSING = "Special Closing"
@Field static final String PARTIALCLOSING = "Partial Closing"
@Field static final String USEROPENING = "User Opening"
@Field static final String SPECIALOPENING = "Special Opening"
@Field static final String PANELBATTERYTROUBLE = "Panel Battery Trouble"
@Field static final String PANELBATTERYTROUBLERESTORED = "Panel Battery Trouble Restored"
@Field static final String PANELACTROUBLE = "Panel AC Trouble"
@Field static final String PANELACTROUBLERESTORED = "Panel AC Trouble Restored"
@Field static final String SYSTEMBELLTROUBLE = "System Bell Trouble"
@Field static final String SYSTEMBELLTROUBLERESTORED = "System Bell Trouble Restored"
@Field static final String FTCTROUBLE = "FTC Trouble"
@Field static final String FTCTROUBLERESTORED = "FTC Trouble Restored"
@Field static final String BUFFERNEARFULL = "Buffer Near Full"
@Field static final String GENERALSYSTEMTAMPER = "General System Tamper"
@Field static final String GENERALSYSTEMTAMPERRESTORED = "General System Tamper Restored"
@Field static final String TROUBLELEDON = "Trouble LED ON"
@Field static final String TROUBLELEDOFF = "Trouble LED OFF"
@Field static final String FIRETROUBLEALARM = "Fire Trouble Alarm"
@Field static final String FIRETROUBLEALARMRESTORED = "Fire Trouble Alarm Restored"
@Field static final String VERBOSETROUBLESTATUS = "Verbose Trouble Status"
@Field static final String CODEREQUIRED = "Code Required"
@Field static final String COMMANDOUTPUTPRESSED = "Command Output Pressed"
@Field static final String MASTERCODEREQUIRED = "Master Code Required"
@Field static final String INSTALLERSCODEREQUIRED = "Installers Code Required"
@Field static final String PASSWORDINCORRECT = "Installers Code Required"
@Field static final String LOGINSUCCESSFUL = "Login Successful"
@Field static final String LOGINTIMEOUT = "Time out.  You did not send password within 10 seconds"
@Field static final String APIFAULT = "API Command Syntax Error"
@Field static final String LOGINPROMPT = "Send Login"

@Field static final String SETUSERCODEINITIALIZE = "SETUSERCODEINITIALIZE"
@Field static final String SETUSERCODESEND = "SETUSERCODESEND"
@Field static final String SETUSERCODECOMPLETE = "SETUSERCODECOMPLETE"

@Field static final String DELETEUSERCODEINITIALIZE = "DELETEUSERCODEINITIALIZE"
@Field static final String DELETEUSERCODE = "DELETEUSERCODE"
@Field static final String DELETEUSERCOMPLETE = "DELETEUSERCOMPLETE"

@Field final Map 	tpiResponses = [
    500: COMMANDACCEPTED,
    501: COMMANDERROR,
    502: SYSTEMERROR,
	  502020: APIFAULT,
    505: LOGININTERACTION,
    5050: PASSWORDINCORRECT,
    5051: LOGINSUCCESSFUL,
    5052: LOGINTIMEOUT,
    5053: LOGINPROMPT,
    510: KEYPADLEDSTATE,
    51091: PROGRAMMINGON,
    51090: PROGRAMMINGOFF,
    511: LEDFLASHSTATE,
    550: TIMEDATEBROADCAST,
    560: RINGDETECTED,
    561: INDOORTEMPBROADCAST,
    562: OUTDOORTEMPBROADCAST,
    601: ZONEALARM,
	6011001: ZONE1ALARM,
	6011002: ZONE2ALARM,
	6011003: ZONE3ALARM,
	6011004: ZONE4ALARM,
	6011005: ZONE5ALARM,
	6011006: ZONE6ALARM,
	6011007: ZONE7ALARM,
	6011008: ZONE8ALARM,
    602: ZONEALARMRESTORE,
	6021001: ZONE1ALARMRESTORE,
	6021002: ZONE2ALARMRESTORE,
	6021003: ZONE3ALARMRESTORE,
	6021004: ZONE4ALARMRESTORE,
	6021005: ZONE5ALARMRESTORE,
	6021006: ZONE6ALARMRESTORE,
	6021007: ZONE7ALARMRESTORE,
	6021008: ZONE8ALARMRESTORE,
    603: ZONETAMPER,
	604: ZONETAMPERRESTORE,
    605: ZONEFAULT,
    606: ZONEFAULTRESTORED,
    609: ZONEOPEN,
    610: ZONERESTORED,
    615: TIMERDUMP,
    616: BYPASSEDZONEBITFIELDDUMP,
    620: DURESSALARM,
    621: FKEYALARM,
    622: FKEYRESTORED,
    623: AKEYALARM,
    624: AKEYRESTORED,
    625: PKEYALARM,
    626: PKEYRESTORED,
    631: TWOWIRESMOKEAUXALARM,
    632: TWOWIRESMOKEAUXRESTORED,
    650: PARTITIONREADY,
    651: PARTITIONNOTREADY,
	652: PARTITIONARMEDSTATE,
    65210: PARTITIONARMEDAWAY,
	65211: PARTITIONARMEDHOME,
    653: PARTITIONNOTREADYFORCEARMINGENABLED,
    654: PARTITIONINALARM,
    655: PARTITIONDISARMED,
    656: EXITDELAY,
   	657: ENTRYDELAY,
    658: KEYPADLOCKOUT,
    659: PARTITIONFAILEDTOARM,
    660: PFMOUTPUT,
   	663: CHIMEENABLED,
    664: CHIMEDISABLED,
    670: INVALIDACCESSCODE,
    671: FUNCTIONNOTAVAILABLE,
    672: FAILURETOARM,
    673: PARTITIONISBUSY,
    674: SYSTEMARMINGPROGRESS,
    680: SYSTEMININSTALLERSMODE,
    700: USERCLOSING,
    701: SPECIALCLOSING,
    702: PARTIALCLOSING,
   	750: USEROPENING,
    751: SPECIALOPENING,
    800: PANELBATTERYTROUBLE,
    801: PANELBATTERYTROUBLERESTORED,
    802: PANELACTROUBLE,
   	803: PANELACTROUBLERESTORED,
    806: SYSTEMBELLTROUBLE,
    807: SYSTEMBELLTROUBLERESTORED,
    814: FTCTROUBLE,
    815: FTCTROUBLERESTORED,
    816: BUFFERNEARFULL,
    829: GENERALSYSTEMTAMPER,
    830: GENERALSYSTEMTAMPERRESTORED,
    840: TROUBLELEDON,
    841: TROUBLELEDOFF,
    842: FIRETROUBLEALARM,
    843: FIRETROUBLEALARMRESTORED,
    849: VERBOSETROUBLESTATUS,
    900: CODEREQUIRED,
    912: COMMANDOUTPUTPRESSED,
    921: MASTERCODEREQUIRED,
    922: INSTALLERSCODEREQUIRED
]
@Field final Map	tpiCommands = [
		Login: "005",
		Poll: "000",
		TimeStampOn: "0550",
		TimeStampOff: "0551",
		StatusReport: "001",
		Disarm: "0401",
		ToggleChime: "0711*4",
		ArmHome: "0311",
		ArmAway: "0301",
	    ArmAwayZeroEntry: "0321",
	    PanicFire: "0601",
	    PanicAmbulance: "0602",
	    PanicPolice: "0603",
        CodeSend: "200",
        EnterUserCodeProgramming: "0721", 
        SendKeyStroke: "0711"
]

/***********************************************************************************************************************
* Version: 0.8.1
*   Fix logging
*   Fix panel type initialization
*   Fix Disarm bug for DSC
* Version: 0.8
*   Vista (Honeywell) support
* Version: 0.7
*   Override armState if panel sends Armed Home
* Version: 0.6
*   Fix LED State, Thanks cybrmage
* Version: 0.5
*   Holistic Refactor
*   Program User Codes
*
* Version: 0.4
*   Arm/Disarm user and special parse
* 
* Version: 0.3.6
*   Fixed Initialization
*   Fixed Telnet Failed Routine
*   Fixed 'Backwards' Arm states
*
* Version: 0.3.5
*   Fixed regex matching for timestamps.
*
* Version: 0.3.4
*   Added Armed Away and Armed Home States, including HSM instructions
*   Added Polling Rate - Default State is Disabled
*   If polling is enabled, it should recover from an Envisalink reboot within two time intervals
*   Added Status Report on initialize to re-sync Alarm State
*   Fixed autoInactive value from 0 to disabled, to resolve motion sensor errors
*
* Version: 0.3.3
*	Hubitat suggested changes, removing regex libraries
*
* Version: 0.3.2
*	Fixed disarm state variable getting out of sync because of reboots or crashed hub during events.
*
* Version: 0.3.0
* 	Fixed Login Response Message
*	Improved Connection Routine
*	Improved Error Logging
*	Integrations
*
* Version: 0.2.1
* 	Added Login Command to Map
*
* Version: 0.2.0
* 	Better response and Command Mapping for easy adaption

* Version: 0.17.0
*	Added TTS
* 	Locks
*	Notifications
*	Unified Logging
*	Syncing Versioning
*
* Version: 0.15.0
*	Version Sync with App
*   Add deeper integration with HSM
*
* Version: 0.13.0
*	Fixed Zone Type Conversion (Always setting up Motion Sensor)
*
* Version: 0.13.0
*	Adding debug switch for reducing logging
*	Move this section to the bottom of the file
*
* Version: 0.12.1
*	Spelling Error
*
* Version: 0.12.0
*	Small State Fix for Motion
*
Version: 0.11.0
*	Added Motion Zone Capability
*
* Version: 0.10.0
*
* 	Just the basics.
*		Establish Telnet with Envisalink
* 		Interpret Incoming Messages from Envisalink
*		Arm Away
*		Arm Home
*	 	Disarm
* 		Switch to Arm Away and Disarm
*	 	Status Report
*		Toggle Chime
*		Toggle Timestamp
*	 	Send Raw Message
*		Create Child Virtual Contacts (Zones)
*		Zone Open and Restored (Closed) Implementation
*		Error Codes
*
*/
