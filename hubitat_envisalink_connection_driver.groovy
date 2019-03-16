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
        command "sendMsg", ["String"]
        command "StatusReport"
        command "ArmAway"
        command "ArmHome"
        //command "SoundAlarm"
        command "Disarm"
        command "ChimeToggle"
        command "ToggleTimeStamp"
        
        
        attribute   "Status", "string"
	}

	preferences {
		input("ip", "text", title: "IP Address", description: "ip", required: true)
        input("passwd", "text", title: "Password", description: "password", required: true)
        input("code", "text", title: "Code", description: "code", required: true)
	}
}

//general handlers
def installed() {
	ifDebug("installed...")
    initialize()
   }

def updated() {
	ifDebug("updated...")
    ifDebug("Configuring IP: ${ip}, Code: ${code}, Password: ${passwd}")
	initialize()
}

def initialize() {
   runIn(3, "telnetConnection")
}


def uninstalled() {
    telnetClose() 
	removeChildDevices(getChildDevices())
}

//envisalink calls
def on(){
    ifDebug("On")
    ArmAway()
}

def off(){
 	ifDebug("Off")
    Disarm()
}

def ArmAway(){
	ifDebug("ArmAway()")
    def message = tpiCommands["ArmAway"]
    sendMsg(message)
}

def ArmHome(){
 	ifDebug("armHome()")
    def message = tpiCommands["ArmHome"]
    sendMsg(message)
}

def both(){
    ifDebug("both()")
 	siren()
    strobe()
}

def ChimeToggle(){
	ifDebug("ChimeToggle()")
    def message = tpiCommands["ToggleChime"]
    sendMsg(message)
}

def Disarm(){
 	ifDebug("Disarm()")
    def message = tpiCommands["Disarm"] + code
    sendMsg(message)
}

def SoundAlarm(){
 	ifDebug("Sound Alarm : NOT IMPLEMENTED")
}

def siren(){
	ifDebug("Siren : NOT IMPLEMENTED")
}

def StatusReport(){
	sendMsg(tpiCommands["StatusReport"])
}

def strobe(){
 	ifDebug("Stobe : NOT IMPLEMENTED") 
    //if allDevices =  getChildDevices()
}

def ToggleTimeStamp(){
    ifDebug("Toggle Time Stamp")
    def message
    if (state.timeStampOn)
    {
    	message = tpiCommands["TimeStampOn"]
    }
    else{
     	message = tpiCommands["TimeStampOff"]
    }
    sendMsg(message)
}

//actions
def createZone(zoneInfo){
    log.info "Creating ${zoneInfo.zoneName} with deviceNetworkId = ${zoneInfo.deviceNetworkId} of type: ${zoneInfo.zoneType}"
    def newDevice
    if (zoneInfo.zoneType == "0")
    {
    	addChildDevice("hubitat", "Virtual Contact Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneName])
    } else {
     	addChildDevice("hubitat", "Virtual Motion Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneName])   
        newDevice = getChildDevice(zoneInfo.deviceNetworkId)
        newDevice.updateSetting("autoInactive",[type:"enum", value:0])
    }
    
}

def removeZone(zoneInfo){
    log.info "Removing ${zoneInfo.zoneName} with deviceNetworkId = ${zoneInfo.deviceNetworkId}"
    deleteChildDevice(zoneInfo.deviceNetworkId)
}

private parse(String message) {
    ifDebug("Parsing Incoming message: " + message)
    message = preProcessMessage(message)
	ifDebug("${tpiResponses[message.take(3) as int]}")


    if(tpiResponses[message.take(3) as int] == SYSTEMERROR) {
		if(tpiResponses[message.take(6) as int] == APIFAULT) {
			ifDebug(APIFAULT)
		}
        systemError(message)
    }
	
	if(tpiResponses[message.take(3) as int] == ZONEOPEN) {
        zoneOpen(message)
    }
	
	if(tpiResponses[message.take(3) as int] == ZONERESTORED) {
         zoneClosed(message)
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONREADY) {
         sendEvent(name:"Status", value: PARTITIONREADY, displayed:false, isStateChange: true)
		sendEvent(name: "switch", value: "off")
		state.armState = "disarmed"
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONNOTREADY) {
         sendEvent(name:"Status", value: PARTITIONNOTREADY, displayed:false, isStateChange: true)
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONARMED) {
        sendEvent(name:"Status", value: PARTITIONARMED, displayed:false, isStateChange: true)
		sendEvent(name: "switch", value: "on")
        systemArmed()
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONNOTREADYFORCEARMINGENABLED) {
	 	sendEvent(name:"Status", value: PARTITIONNOTREADYFORCEARMINGENABLED, displayed:false, isStateChange: true)
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONINALARM) {
		sendEvent(name:"Status", value: PARTITIONINALARM, displayed:false, isStateChange: true)
		alarming()
    }
	
	if(tpiResponses[message.take(3) as int] == PARTITIONDISARMED) {
        sendEvent(name:"Status", value: PARTITIONDISARMED, displayed:false, isStateChange: true)
		sendEvent(name: "switch", value: "off")
        disarming()
    }
	
	if(tpiResponses[message.take(3) as int] == EXITDELAY) {
        sendEvent(name:"Status", value: EXITDELAY, displayed:false, isStateChange: true)
		exitDelay()
    }
	
	if(tpiResponses[message.take(3) as int] == ENTRYDELAY) {
        sendEvent(name:"Status", value: ENTRYDELAY, displayed:false, isStateChange: true)
		entryDelay()
    }
	
	if(tpiResponses[message.take(3) as int] == KEYPADLOCKOUT) {
        sendEvent(name:"Status", value: KEYPADLOCKOUT, displayed:false, isStateChange: true)
    }
	
	if(tpiResponses[message.take(3) as int] == LOGININTERACTION) {
		if(tpiResponses[message.take(4) as int] == LOGINPROMPT) {
			sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
			ifDebug("Connection to Envisalink established")
			state.reTryCount = 0
			sendLogin()
          	ifDebug(LOGINPROMPT)
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
}

def zoneOpen(message){
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

def zoneClosed(message){
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

def systemError(message){
    def substringCount = message.size() - 3
    message = message.substring(substringCount).take(3).replaceAll('0', '')
    logError("System Error: ${message} - ${errorCodes.getAt(message)}")
}

def disarming(){
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

def systemArmed(){
	if (state.armState != "armed"){
		ifDebug("armed")
		state.armState = "armed"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus == "disarmed")
		{
			sendLocationEvent(name: "hsmSetArm", value: "armHome")
		}
	}
}

def entryDelay(){
	 ifDebug("entryDelay")
		state.armState = "intrusion"
		parent.speakEntryDelay()
}

def exitDelay(){
	 ifDebug("exitDelay")
		state.armState = "exit"
		parent.speakExitDelay()
}

def alarming(){
	 ifDebug("alarm")
		state.armState = "alarming"
		parent.speakAlarm()
}

//helpers
private checkTimeStamp(message){
    if (message ==~ timeStampPattern){
        ifDebug("Time Stamp Found")
        	state.timeStampOn = true;
        	message = message.replaceAll(timeStampPattern, "")
        	ifDebug("Time Stamp Remove ${message}")
        }
        else{
            state.timeStampOn = false;
        	ifDebug("Time Stamp Not Found")
        }
    return message
}

private generateChksum(String cmdToSend){
		def cmdArray = cmdToSend.toCharArray()
        def cmdSum = 0
        cmdArray.each { cmdSum += (int)it }
        def chkSumStr = DataType.pack(cmdSum, 0x08)
        if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
        cmdToSend += chkSumStr
    	cmdToSend
}

private preProcessMessage(message){
    ifDebug("Preprocessing Message")
 	message = checkTimeStamp(message)
    //strip checksum
    message = message.take(message.size() - 2)
    ifDebug("Stripping Checksum: ${message}")
    return message
}

def poll() {
    return new hubitat.device.HubAction(tpiCommands["Poll"], hubitat.device.Protocol.TELNET)
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

private sendLogin(){
    def cmdToSend =  tpiCommands["Login"] + "${passwd}"
    def cmdArray = cmdToSend.toCharArray()
    def cmdSum = 0
    cmdArray.each { cmdSum += (int)it }
    def chkSumStr = DataType.pack(cmdSum, 0x08)
    if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
    cmdToSend += chkSumStr
    cmdToSend = cmdToSend + "\r\n"
    sendHubCommand(new hubitat.device.HubAction(cmdToSend, hubitat.device.Protocol.TELNET))   
}

def sendMsg(String s) {
    s = generateChksum(s)
    ifDebug("sendMsg $s")
	return new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET)
}

//Telnet
def getReTry(Boolean inc){
	def reTry = (state.reTryCount ?: 0).toInteger()
	if (inc) reTry++
	state.reTryCount = reTry
	return reTry
}


def telnetConnection(){
 	telnetClose() 
	try {
		//open telnet connection
		telnetConnect([termChars:[13,10]], ip, 4025, null, null)
		//give it a chance to start
		pauseExecution(1000)
		
        //poll()
	} catch(e) {
		logError("initialize error: ${e.message}")
	}	
}

def telnetStatus(String status){
	logError("telnetStatus- error: ${status}")
	if (status != "receive error: Stream is closed"){
		getReTry(true)
		logError("Telnet connection dropped...")
		initialize()
	} else {
		logError("Telnet is restarting...")
	}
}

private ifDebug(msg){  
	parent.ifDebug('Connection Driver: ' + msg)
}

private logError(msg){  
	parent.logError('Connection Driver: ' + msg)
}

@Field String timeStampPattern = ~/^\d{2}:\d{2}:\d{2} /  

@Field final Map 	errorCodes = [
    0: 	'No Error', 
    1: 	'Receive Buffer Overrun',
    2: 	'Receive Buffer Overflow',
    3: 	'Transmit Buffer Overflow',
    10: 'Keybus Transmit Buffer Overrun',
    11: 'Keybus Transmit Time Timeout',
    12: 'Keybus Transmit Mode Timeout', 
    13: 'Keybus Transmit Keystring Timeout',
    14: 'Keybus Interface Not Functioning (the TPI cannot communicate with the security system)',
    15: 'Keybus Busy (Attempting to Disarm or Arm with user code)',
    16: 'Keybus Busy – Lockout (The panel is currently in Keypad Lockout – too many disarm attempts)',
    17: 'Keybus Busy – Installers Mode (Panel is in installers mode, most functions are unavailable)',
    18: 'Keybus Busy – General Busy (The requested partition is busy)',
    20: 'API Command Syntax Error',
    21: 'API Command Partition Error (Requested Partition is out of bounds)',
    22: 'API Command Not Supported',
    23: 'API System Not Armed (sent in response to a disarm command)',
    24: 'API System Not Ready to Arm (system is either not-secure, in exit-delay, or already armed',
    25: 'API Command Invalid Length 26 API User Code not Required',
    27: 'API Invalid Characters in Command (no alpha characters are allowed except for checksum'
]

@Field static final String COMMANDACCEPTED = "Command Accepted"
@Field static final String KEYPADLEDSTATE = "Keypad LED State"
@Field static final String COMMANDERROR = "Command Error"
@Field static final String SYSTEMERROR = "System Error"
@Field static final String LOGININTERACTION = "Login Interaction"
@Field static final String LEDFLASHSTATE = "Keypad LED FLASH state"
@Field static final String TIMEDATEBROADCAST = "Time - Date Broadcast"
@Field static final String RINGDETECTED = "Ring Detected"
@Field static final String INDOORTEMPBROADCAST = "Indoor Temp Broadcast"
@Field static final String OUTDOORTEMPBROADCAST = "Outdoor Temperature Broadcast"
@Field static final String ZONEALARM = "Zone Alarm"
@Field static final String ZONEALARMRESTORE = "Zone Alarm RestoreD"
@Field static final String ZONETAMPER = "Zone Tamper"
@Field static final String ZONETAMPERRESTORE = "Zone Tamper RestoreD"
@Field static final String ZONEFAULT = "Zone Fault"
@Field static final String ZONEFAULTRESTORED = "Zone Fault RestoreD"
@Field static final String ZONEOPEN = "Zone Open"
@Field static final String ZONERESTORED = "Zone Restored"
@Field static final String TIMERDUMP = "Envisalink Zone Timer Dump"
@Field static final String BYPASSEDZONEBITFIELDDUMP = "Bypassed Zones Bitfield Dump"
@Field static final String DURESSALARM = "Duress Alarm"
@Field static final String FKEYALARM = "F Key Alarm"
@Field static final String FKEYRESTORED = "F Key Restored"
@Field static final String AKEYALARM = "A Key Alarm"
@Field static final String AKEYRESTORED = "A Key Restored"
@Field static final String PKEYALARM = "P Key Alarm"
@Field static final String PKEYRESTORED = "P Key Restored"
@Field static final String TWOWIRESMOKEAUXALARM = "2-Wire Smoke Aux Alarm"
@Field static final String TWOWIRESMOKEAUXRESTORED = "2-Wire Smoke Aux Restored"
@Field static final String PARTITIONREADY = "Ready"
@Field static final String PARTITIONNOTREADY = "Not Ready"
@Field static final String PARTITIONARMED = "Armed"
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
    511: LEDFLASHSTATE,
    550: TIMEDATEBROADCAST,
    560: RINGDETECTED,
    561: INDOORTEMPBROADCAST,
    562: OUTDOORTEMPBROADCAST,
    601: ZONEALARM,
    602: ZONEALARMRESTORE,
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
    652: PARTITIONARMED,
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
		ArmAway: "0301"
]

/***********************************************************************************************************************
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
* Version: 0.13.0
*	Adding debug switch for reducing logging
*	Move this section to the bottom of the file
*
* Version: 0.12.1
*	Spelling Error
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
