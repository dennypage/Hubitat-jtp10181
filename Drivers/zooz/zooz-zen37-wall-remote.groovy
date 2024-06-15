/*
 *  Zooz ZEN37 Wall Remote
 *    - Model: ZEN37
 *
 *  For Support, Information, and Updates:
 *  https://community.hubitat.com/t/zooz-zen37-wall-remote/129335
 *  https://github.com/jtp10181/Hubitat/tree/main/Drivers/zooz
 *

Changelog:

## [1.0.0] - 2024-06-15 (@jtp10181)
  - Added support for battery status events
  - Added wake up interval setting
  - Added support for associations
  - Added singleThreaded flag
  - Fixed install sequence so it will fully configure at initial pairing
  - Updated library and common code

## [0.1.0] - 2023-12-08 (@jtp10181)
  - Initial Release

 *  Copyright 2023-2024 Jeff Page
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *
*/

import groovy.transform.Field

@Field static final String VERSION = "1.0.0"
@Field static final String DRIVER = "Zooz-ZEN37"
@Field static final String COMM_LINK = "https://community.hubitat.com/t/zooz-zen37-wall-remote/129335"
@Field static final Map deviceModelNames = ["7000:F003":"ZEN37"]

metadata {
	definition (
		name: "Zooz ZEN37 Wall Remote Advanced",
		namespace: "jtp10181",
		author: "Jeff Page (@jtp10181)",
		singleThreaded: true,
		importUrl: "https://raw.githubusercontent.com/jtp10181/Hubitat/main/Drivers/zooz/zooz-zen37-wall-remote.groovy"
	) {
		capability "Actuator"
		capability "Battery"
		capability "PushableButton"
		capability "HoldableButton"
		capability "ReleasableButton"
		capability "DoubleTapableButton"

		command "fullConfigure"
		command "forceRefresh"

		//DEBUGGING
		//command "debugShowVars"

		attribute "syncStatus", "string"
		attribute "batteryStatus", "string"

		fingerprint mfr:"027A", prod:"7000", deviceId:"F003", inClusters:"0x00,0x00", controllerType: "ZWV"  //Zooz ZEN37 Wall Remote
	}

	preferences {
		configParams.each { param ->
			if (!param.hidden) {
				if (param.options) {
					Integer paramVal = getParamValue(param)
					input "configParam${param.num}", "enum",
						title: fmtTitle("${param.title}"),
						description: fmtDesc("• Parameter #${param.num}, Selected: ${paramVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
						defaultValue: param.defaultVal,
						options: param.options,
						required: false
				}
				else if (param.range) {
					input "configParam${param.num}", "number",
						title: fmtTitle("${param.title}"),
						description: fmtDesc("• Parameter #${param.num}, Range: ${(param.range).toString()}, DEFAULT: ${param.defaultVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
						defaultValue: param.defaultVal,
						range: param.range,
						required: false
				}
			}
		}

		if (!isLongRange()) {
			input "assocEnabled", "bool", defaultValue: false,
				title: fmtTitle("Show Association Settings"),
				description: fmtDesc("Turn on and Save to show the Association Settings")

			if (assocEnabled) {
				for(int i in 2..maxAssocGroups) {
					input "assocDNI$i", "string", required: false,
						title: fmtTitle("Device Associations - Group $i (${AssocGrpNames[i]})"),
						description: fmtDesc("${AssocGrpInfo[i]}. Supports up to ${maxAssocNodes} Hex Device IDs separated by commas. Save as blank or 0 to clear.")
				}
			}
		} else {
			input "assocEnabled", "hidden", title: fmtTitle("Associations Not Available"),
				description: fmtDesc("Associations are not available when device is paired in Long Range mode")
		}

		input "wakeUpInt", "number",
			title: fmtTitle("Wake-up Interval (hours)"),
			description: fmtDesc("How often the device will wake up to receive commands from the hub"),
			defaultValue: 12, range: "1..24"
	}
}

void debugShowVars() {
	log.warn "settings ${settings.hashCode()} ${settings}"
	log.warn "paramsList ${paramsList.hashCode()} ${paramsList}"
	log.warn "paramsMap ${paramsMap.hashCode()} ${paramsMap}"
}

//Association Settings
@Field static final int maxAssocGroups = 9
@Field static final int maxAssocNodes = 5
@Field static final TreeMap<Integer,String> AssocGrpNames = [
	2:"On/Off Top", 3:"On/Off Bottom", 4:"Dimming Top", 5:"Dimming Bottom", 6:"On/Off Button 1", 7:"On/Off Button 2", 8:"On/Off Button 3", 9:"On/Off Button 4"
]
@Field static final TreeMap<Integer,String> AssocGrpInfo = [
	2:"Basic on (button 1: top large button) and off (button 2: bottom large button)", 
	3:"Basic on (button 3: small left button) and off (button 4: small right button)", 
	4:"Multilevel start level change (held) and stop level change (released) for buttons 1 (up) and 2 (down)", 
	5:"Multilevel start level change (held) and stop level change (released) for buttons 3 (up) and 4 (down)", 
	6:"Basic on/off toggle when button 1 is pressed",
	7:"Basic on/off toggle when button 2 is pressed",
	8:"Basic on/off toggle when button 3 is pressed",
	9:"Basic on/off toggle when button 4 is pressed"
]

/*** Static Lists and Settings ***/
@Field static final TreeMap<Integer,String> ledColorOptions = [
   0:"White", 1:"Purple", 2:"Orange", 3:"Cyan", 4:"Red", 5:"Green", 6:"Blue"
]

//Main Parameters Listing
@Field static Map<String, Map> paramsMap =
[
	batteryAlarm: [ num: 1,
		title: "Low Battery Alert Threshold",
		size: 1, defaultVal: 5,
		range: "5..10"
	],
	ledColor1: [ num: 2,
		title: "LED Color (Button 1)",
		size: 1, defaultVal: 0,
		options: [:] //ledColorOptions
	],
	ledColor2: [ num: 3,
		title: "LED Color (Button 2)",
		size: 1, defaultVal: 1,
		options: [:] //ledColorOptions
	],
	ledColor3: [ num: 4,
		title: "LED Color (Button 3)",
		size: 1, defaultVal: 2,
		options: [:] //ledColorOptions
	],
	ledColor4: [ num: 5,
		title: "LED Color (Button 4)",
		size: 1, defaultVal: 5,
		options: [:] //ledColorOptions
	],
	ledBrightness: [ num: 6,
		title: "LED Indicator Brightness",
		size: 1, defaultVal: 5,
		options: [0:"OFF", 1:"10%", 2:"20%", 3:"30%", 4:"40%", 5:"50%", 6:"60%", 7:"70%", 8:"80%", 9:"90%", 10:"100%"]
	],
	dimmingDuration: [ num: 7,
		title: "Remote Dimming Duration",
		description: "Time (seconds) to go from 0 to 100 brightness on associated devices",
		size: 1, defaultVal: 5,
		range: "1..99"
	]
]

/* ZEN37 v0.07
CommandClassReport - class:0x55, version:2   (Transport Service)
CommandClassReport - class:0x59, version:3   (Association Group Information (AGI))
CommandClassReport - class:0x5A, version:1   (Device Reset Locally)
CommandClassReport - class:0x5B, version:3   (Central Scene)
CommandClassReport - class:0x5E, version:2   (Z-Wave Plus Info)
CommandClassReport - class:0x6C, version:1   (Supervision)
CommandClassReport - class:0x70, version:4   (Configuration)
CommandClassReport - class:0x71, version:8   (Notification)
CommandClassReport - class:0x72, version:2   (Manufacturer Specific)
CommandClassReport - class:0x73, version:1   (Powerlevel)
CommandClassReport - class:0x7A, version:5   (Firmware Update Meta Data)
CommandClassReport - class:0x80, version:1   (Battery)
CommandClassReport - class:0x84, version:2   (Wake Up)
CommandClassReport - class:0x85, version:2   (Association)
CommandClassReport - class:0x86, version:3   (Version)
CommandClassReport - class:0x87, version:3   (Indicator)
CommandClassReport - class:0x8E, version:3   (Multi Channel Association)
CommandClassReport - class:0x9F, version:1   (Security 2)
*/

//Set Command Class Versions
@Field static final Map commandClassVersions = [
	0x5B: 3,   // centralScene
	0x6C: 1,   // supervision
	0x70: 1,   // configuration
	0x71: 8,   // notification
	0x85: 2,   // association
	0x86: 2,   // version
	0x8E: 3,   // multiChannelAssociation
]


/*******************************************************************
 ***** Core Functions
********************************************************************/
void installed() {
	logWarn "installed..."
	state.resyncAll = true
	runIn(2, runWakeupCmds)
	sendCommands(getRefreshCmds(),400)
}

void fullConfigure() {
	logWarn "fullConfigure..."

	if (!pendingChanges || state.resyncAll == null) {
		logForceWakeupMessage "Full Re-Configure"
		state.resyncAll = true
	} else {
		logForceWakeupMessage "Pending Configuration Changes"
	}

	updateSyncingStatus(1)
}

void updated() {
	logDebug "updated..."
	checkLogLevel()

	if (!firmwareVersion || !state.deviceModel) {
		state.resyncAll = true
		state.pendingRefresh = true
		logForceWakeupMessage "Full Re-Configure and Refresh"
	}

	if (pendingChanges) {
		logForceWakeupMessage "Pending Configuration Changes"
	}
	else if (!state.resyncAll && !state.pendingRefresh) {
		state.remove("INFO")
	}

	updateSyncingStatus(1)
}

void forceRefresh() {
	logDebug "refresh..."
	state.pendingRefresh = true
	logForceWakeupMessage "State Info Refresh"
}


/*******************************************************************
 ***** Driver Commands
********************************************************************/
/*** Capabilities ***/
//Button commands required with capabilities
void push(buttonId) { sendBasicButtonEvent(buttonId, "pushed") }
void hold(buttonId) { sendBasicButtonEvent(buttonId, "held") }
void release(buttonId) { sendBasicButtonEvent(buttonId, "released") }
void doubleTap(buttonId) { sendBasicButtonEvent(buttonId, "doubleTapped") }

/*** Custom Commands ***/


/*******************************************************************
 ***** Z-Wave Reports
********************************************************************/
void parse(String description) {
	zwaveParse(description)
	sendEvent(name:"numberOfButtons", value:20)
}
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=0) {
	zwaveSupervision(cmd,ep)
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	logTrace "${cmd}"
	updateSyncingStatus()

	Map param = getParam(cmd.parameterNumber)
	Integer val = cmd.scaledConfigurationValue

	if (param) {
		//Convert scaled signed integer to unsigned
		Long sizeFactor = Math.pow(256,param.size).round()
		if (val < 0) { val += sizeFactor }

		logDebug "${param.name} - ${param.title} (#${param.num}) = ${val.toString()}"
		setParamStoredValue(param.num, val)
	}
	else {
		logDebug "Parameter #${cmd.parameterNumber} = ${val.toString()}"
	}
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	logTrace "${cmd}"
	updateSyncingStatus()

	Integer grp = cmd.groupingIdentifier

	if (grp == 1) {
		logDebug "Lifeline Association: ${cmd.nodeId}"
		state.group1Assoc = (cmd.nodeId == [zwaveHubNodeId]) ? true : false
	}
	else if (grp > 1 && grp <= maxAssocGroups) {
		String dnis = convertIntListToHexList(cmd.nodeId)?.join(", ")
		logDebug "Confirmed Group $grp Association: " + (cmd.nodeId.size()>0 ? "${dnis} // ${cmd.nodeId}" : "None")

		if (cmd.nodeId.size() > 0) {
			if (!state.assocNodes) state.assocNodes = [:]
			state.assocNodes["$grp"] = cmd.nodeId
		} else {
			state.assocNodes?.remove("$grp" as String)
		}
		device.updateSetting("assocDNI$grp", [value:"${dnis}", type:"string"])
	}
	else {
		logDebug "Unhandled Group: $cmd"
	}
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"

	Integer batLvl = cmd.batteryLevel
	if (batLvl == 0xFF) {
		batLvl = 1
		logWarn "LOW BATTERY WARNING"
	}

	batLvl = validateRange(batLvl, 100, 1, 100)
	String descText = "battery level is ${batLvl}%"
	sendEventLog(name:"battery", value:batLvl, unit:"%", desc:descText, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
	logTrace "${cmd}"
	BigDecimal wakeHrs = safeToDec(cmd.seconds/3600,0,2)
	logDebug "WakeUp Interval is $cmd.seconds seconds ($wakeHrs hours)"
	device.updateDataValue("zwWakeupInterval", "${cmd.seconds}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"
	logDebug "WakeUp Notification Received"
	runWakeupCmds()
}

void runWakeupCmds() {
	List<String> cmds = ["delay 0"]
	cmds << batteryGetCmd()

	//Refresh all if requested
	if (state.pendingRefresh) { cmds += getRefreshCmds() }
	//Any configuration needed
	cmds += getConfigureCmds()

	//This needs a longer delay
	cmds << "delay 1400" << wakeUpNoMoreInfoCmd()

	//Clear pending status
	state.resyncAll = false
	state.pendingRefresh = false
	state.remove("INFO")
	
	sendCommands(cmds,300)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"

	Integer physicalButtons = 4
	Integer btnBaseNum = cmd.sceneNumber ?: 0
	Map sceneEvt = [name: "", value: btnBaseNum, desc: "", type:"physical", isStateChange:true]
	cmd.keyAttributes = cmd.keyAttributes as Integer
	String btnDesc = ""

	//DoubleTapped
	if (btnBaseNum && cmd.keyAttributes == 3) {
		sceneEvt.name = "doubleTapped"
		sceneEvt.desc = "button ${sceneEvt.value} ${sceneEvt.name}"
		sendEventLog(sceneEvt)
	}

	if (cmd.keyAttributes == 2) sceneEvt.name = "held"
	else if (cmd.keyAttributes == 1) sceneEvt.name = "released"
	else sceneEvt.name = "pushed"

	if (cmd.keyAttributes >= 3) {
		//Adjust button number
		btnDesc = " [button ${btnBaseNum} pushed ${cmd.keyAttributes - 1}x]"
		sceneEvt.value = btnBaseNum + (physicalButtons * (cmd.keyAttributes - 2))
	}

	if (btnBaseNum) {
		sceneEvt.desc = "button ${sceneEvt.value} ${sceneEvt.name}${btnDesc}"
		sendEventLog(sceneEvt)
	}
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"
	switch (cmd.notificationType as Integer) {
		case 0x08:  //Power Management
			sendPowerEvent(cmd.event, cmd.eventParameter[0])
			break
		default:
			logDebug "Unhandled notificationType: ${cmd}"
	}
}


/*******************************************************************
 ***** Event Senders
********************************************************************/
//evt = [name, value, type, unit, desc, isStateChange]
void sendEventLog(Map evt, Integer ep=0) {
	//Set description if not passed in
	evt.descriptionText = evt.desc ?: "${evt.name} set to ${evt.value} ${evt.unit ?: ''}".trim()

	//Main Device Events
	if (device.currentValue(evt.name).toString() != evt.value.toString() || evt.isStateChange) {
		logInfo "${evt.descriptionText}"
	} else {
		logDebug "${evt.descriptionText} [NOT CHANGED]"
	}
	//Always send event to update last activity
	sendEvent(evt)
}

void sendBasicButtonEvent(buttonId, String name) {
	String desc = "button ${buttonId} ${name} (digital)"
	sendEventLog(name:name, value:buttonId, type:"digital", desc:desc, isStateChange:true)
}

void sendPowerEvent(Integer event, Integer parameter) {
	String battStatus
	switch (event as Integer) {
		case 0x00:  //Idle State
			battStatus = "discharging"
			break
		case 0x0C:  //Battery is charging
			battStatus = "charging"
			break
		case 0x0D:  //Battery is fully charged
			battStatus = "charged"
			break
		default:
			logDebug "Unhandled Power Management: ${event}, ${parameter}"
	}

	if (battStatus) {
		sendEventLog(name:"batteryStatus", value:battStatus, desc:"battery is ${battStatus}")
	}
}


/*******************************************************************
 ***** Execute / Build Commands
********************************************************************/
List<String> getConfigureCmds() {
	logDebug "getConfigureCmds..."

	List<String> cmds = []

	Integer wakeSeconds = wakeUpInt ? wakeUpInt*3600 : 43200
	if (state.resyncAll || wakeSeconds != (device.getDataValue("zwWakeupInterval") as Integer)) {
		logDebug "Settting WakeUp Interval to $wakeSeconds seconds"
		cmds << wakeUpIntervalSetCmd(wakeSeconds)
		cmds << wakeUpIntervalGetCmd()
	}
	if (state.resyncAll || !firmwareVersion || !state.deviceModel) {
		cmds << mfgSpecificGetCmd()
		cmds << versionGetCmd()
	}

	cmds += getConfigureAssocsCmds(true)

	configParams.each { param ->
		Integer paramVal = getParamValueAdj(param)
		Integer storedVal = getParamStoredValue(param.num)

		if ((paramVal != null) && (state.resyncAll || (storedVal != paramVal))) {
			logDebug "Changing ${param.name} - ${param.title} (#${param.num}) from ${storedVal} to ${paramVal}"
			cmds += configSetGetCmd(param, paramVal)
		}
	}

	if (state.resyncAll) clearVariables()
	state.resyncAll = false

	if (cmds) updateSyncingStatus(6)

	return cmds ?: []
}

List<String> getRefreshCmds() {
	List<String> cmds = []

	cmds << batteryGetCmd()
	cmds << wakeUpIntervalGetCmd()
	cmds << versionGetCmd()

	return cmds ?: []
}

List getConfigureAssocsCmds(Boolean logging=false) {
	List<String> cmds = []

	if (!state.group1Assoc || state.resyncAll) {
		if (logging) logDebug "Setting lifeline association..."
		cmds << associationSetCmd(1, [zwaveHubNodeId])
		cmds << associationGetCmd(1)
	}

	for (int i = 2; i <= maxAssocGroups; i++) {
		List<String> cmdsEach = []
		List settingNodeIds = getAssocDNIsSettingNodeIds(i)

		//Need to remove first then add in case we are at limit
		List oldNodeIds = state.assocNodes?."$i"?.findAll { !(it in settingNodeIds) }
		if (oldNodeIds) {
			if (logging) logDebug "Removing Group $i Association: ${convertIntListToHexList(oldNodeIds)} // $oldNodeIds"
			cmdsEach << associationRemoveCmd(i, oldNodeIds)
		}

		List newNodeIds = settingNodeIds.findAll { !(it in state.assocNodes?."$i") }
		if (newNodeIds) {
			if (logging) logDebug "Adding Group $i Association: ${convertIntListToHexList(newNodeIds)} // $newNodeIds"
			cmdsEach << associationSetCmd(i, newNodeIds)
		}

		if (cmdsEach || state.resyncAll) {
			cmdsEach << associationGetCmd(i)
			cmds += cmdsEach
		}
	}

	return cmds
}

private logForceWakeupMessage(msg) {
	String helpText = "Quickly press the bottom left button 6 times to wake the device."
	logWarn "${msg} will execute the next time the device wakes up.  ${helpText}"
	state.INFO = "*** ${msg} *** Waiting for device to wake up.  ${helpText}"
}


/*******************************************************************
 ***** Required for Library
********************************************************************/
//These have to be added in after the fact or groovy complains
void fixParamsMap() {
	paramsMap.ledColor1.options << ledColorOptions
	paramsMap.ledColor2.options << ledColorOptions
	paramsMap.ledColor3.options << ledColorOptions
	paramsMap.ledColor4.options << ledColorOptions
	paramsMap['settings'] = [fixed: true]
}

Integer getParamValueAdj(Map param) {
	return getParamValue(param)
}


//#include jtp10181.zwaveDriverLibrary
/*******************************************************************
 *******************************************************************
 ***** Z-Wave Driver Library by Jeff Page (@jtp10181)
 *******************************************************************
********************************************************************

Changelog:
2023-05-10 - First version used in drivers
2023-05-12 - Adjustments to community links
2023-05-14 - Updates for power metering
2023-05-18 - Adding requirement for getParamValueAdj in driver
2023-05-24 - Fix for possible RuntimeException error due to bad cron string
2023-10-25 - Less saving to the configVals data, and some new functions
2023-10-26 - Added some battery shortcut functions
2023-11-08 - Added ability to adjust settings on firmware range
2024-01-28 - Adjusted logging settings for new / upgrade installs, added mfgSpecificReport
2024-06-15 - Added isLongRange function, convert range to string to prevent expansion

********************************************************************/

library (
  author: "Jeff Page (@jtp10181)",
  category: "zwave",
  description: "Z-Wave Driver Library",
  name: "zwaveDriverLibrary",
  namespace: "jtp10181",
  documentationLink: ""
)

/*******************************************************************
 ***** Z-Wave Reports (COMMON)
********************************************************************/
//Include these in Driver
//void parse(String description) {zwaveParse(description)}
//void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {zwaveMultiChannel(cmd)}
//void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=0) {zwaveSupervision(cmd,ep)}

void zwaveParse(String description) {
	hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)

	if (cmd) {
		logTrace "parse: ${description} --PARSED-- ${cmd}"
		zwaveEvent(cmd)
	} else {
		logWarn "Unable to parse: ${description}"
	}

	//Update Last Activity
	updateLastCheckIn()
}

//Decodes Multichannel Encapsulated Commands
void zwaveMultiChannel(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	hubitat.zwave.Command encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
	logTrace "${cmd} --ENCAP-- ${encapsulatedCmd}"

	if (encapsulatedCmd) {
		zwaveEvent(encapsulatedCmd, cmd.sourceEndPoint as Integer)
	} else {
		logWarn "Unable to extract encapsulated cmd from $cmd"
	}
}

//Decodes Supervision Encapsulated Commands (and replies to device)
void zwaveSupervision(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=0) {
	hubitat.zwave.Command encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
	logTrace "${cmd} --ENCAP-- ${encapsulatedCmd}"

	if (encapsulatedCmd) {
		zwaveEvent(encapsulatedCmd, ep)
	} else {
		logWarn "Unable to extract encapsulated cmd from $cmd"
	}

	sendCommands(secureCmd(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0), ep))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	logTrace "${cmd}"

	String fullVersion = String.format("%d.%02d",cmd.firmware0Version,cmd.firmware0SubVersion)
	String zwaveVersion = String.format("%d.%02d",cmd.zWaveProtocolVersion,cmd.zWaveProtocolSubVersion)
	device.updateDataValue("firmwareVersion", fullVersion)
	device.updateDataValue("protocolVersion", zwaveVersion)
	device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")

	logDebug "Received Version Report - Firmware: ${fullVersion}"
	setDevModel(new BigDecimal(fullVersion))
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	logTrace "${cmd}"

	device.updateDataValue("manufacturer",cmd.manufacturerId.toString())
	device.updateDataValue("deviceType",cmd.productTypeId.toString())
	device.updateDataValue("deviceId",cmd.productId.toString())

	logDebug "fingerprint  mfr:\"${hubitat.helper.HexUtils.integerToHexString(cmd.manufacturerId, 2)}\", "+
		"prod:\"${hubitat.helper.HexUtils.integerToHexString(cmd.productTypeId, 2)}\", "+
		"deviceId:\"${hubitat.helper.HexUtils.integerToHexString(cmd.productId, 2)}\", "+
		"inClusters:\"${device.getDataValue("inClusters")}\""+
		(device.getDataValue("secureInClusters") ? ", secureInClusters:\"${device.getDataValue("secureInClusters")}\"" : "")
}

void zwaveEvent(hubitat.zwave.Command cmd, ep=0) {
	logDebug "Unhandled zwaveEvent: $cmd (ep ${ep})"
}


/*******************************************************************
 ***** Z-Wave Command Shortcuts
********************************************************************/
//These send commands to the device either a list or a single command
void sendCommands(List<String> cmds, Long delay=200) {
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

//Single Command
void sendCommands(String cmd) {
	sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

//Consolidated zwave command functions so other code is easier to read
String associationSetCmd(Integer group, List<Integer> nodes) {
	return secureCmd(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

String associationRemoveCmd(Integer group, List<Integer> nodes) {
	return secureCmd(zwave.associationV2.associationRemove(groupingIdentifier: group, nodeId: nodes))
}

String associationGetCmd(Integer group) {
	return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: group))
}

String mcAssociationGetCmd(Integer group) {
	return secureCmd(zwave.multiChannelAssociationV3.multiChannelAssociationGet(groupingIdentifier: group))
}

String versionGetCmd() {
	return secureCmd(zwave.versionV2.versionGet())
}

String mfgSpecificGetCmd() {
	return secureCmd(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
}

String switchBinarySetCmd(Integer value, Integer ep=0) {
	return secureCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: value), ep)
}

String switchBinaryGetCmd(Integer ep=0) {
	return secureCmd(zwave.switchBinaryV1.switchBinaryGet(), ep)
}

String switchMultilevelSetCmd(Integer value, Integer duration, Integer ep=0) {
	return secureCmd(zwave.switchMultilevelV4.switchMultilevelSet(dimmingDuration: duration, value: value), ep)
}

String switchMultilevelGetCmd(Integer ep=0) {
	return secureCmd(zwave.switchMultilevelV4.switchMultilevelGet(), ep)
}

String switchMultilevelStartLvChCmd(Boolean upDown, Integer duration, Integer ep=0) {
	//upDown: false=up, true=down
	return secureCmd(zwave.switchMultilevelV4.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel:1, dimmingDuration: duration), ep)
}

String switchMultilevelStopLvChCmd(Integer ep=0) {
	return secureCmd(zwave.switchMultilevelV4.switchMultilevelStopLevelChange(), ep)
}

String meterGetCmd(meter, Integer ep=0) {
	return secureCmd(zwave.meterV3.meterGet(scale: meter.scale), ep)
}

String meterResetCmd(Integer ep=0) {
	return secureCmd(zwave.meterV3.meterReset(), ep)
}

String wakeUpIntervalGetCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalGet())
}

String wakeUpIntervalSetCmd(val) {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalSet(seconds:val, nodeid:zwaveHubNodeId))
}

String wakeUpNoMoreInfoCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}

String batteryGetCmd() {
	return secureCmd(zwave.batteryV1.batteryGet())
}

String sensorMultilevelGetCmd(sensorType) {
	Integer scale = (temperatureScale == "F" ? 1 : 0)
	return secureCmd(zwave.sensorMultilevelV11.sensorMultilevelGet(scale: scale, sensorType: sensorType))
}

String notificationGetCmd(notificationType, eventType, Integer ep=0) {
	return secureCmd(zwave.notificationV3.notificationGet(notificationType: notificationType, v1AlarmType:0, event: eventType), ep)
}

String configSetCmd(Map param, Integer value) {
	//Convert from unsigned to signed for scaledConfigurationValue
	Long sizeFactor = Math.pow(256,param.size).round()
	if (value >= sizeFactor/2) { value -= sizeFactor }

	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value))
}

String configGetCmd(Map param) {
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

List configSetGetCmd(Map param, Integer value) {
	List<String> cmds = []
	cmds << configSetCmd(param, value)
	cmds << configGetCmd(param)
	return cmds
}


/*******************************************************************
 ***** Z-Wave Encapsulation
********************************************************************/
//Secure and MultiChannel Encapsulate
String secureCmd(String cmd) {
	return zwaveSecureEncap(cmd)
}
String secureCmd(hubitat.zwave.Command cmd, ep=0) {
	return zwaveSecureEncap(multiChannelEncap(cmd, ep))
}

//MultiChannel Encapsulate if needed
//This is called from secureCmd or supervisionEncap, do not call directly
String multiChannelEncap(hubitat.zwave.Command cmd, ep) {
	//logTrace "multiChannelEncap: ${cmd} (ep ${ep})"
	if (ep > 0) {
		cmd = zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
	}
	return cmd.format()
}


/*******************************************************************
 ***** Common Functions
********************************************************************/
/*** Parameter Store Map Functions ***/
@Field static Map<String, Map> configsList = new java.util.concurrent.ConcurrentHashMap()
Integer getParamStoredValue(Integer paramNum) {
	//Using Data (Map) instead of State Variables
	Map configsMap = getParamStoredMap()
	return safeToInt(configsMap[paramNum], null)
}

void setParamStoredValue(Integer paramNum, Integer value) {
	//Using Data (Map) instead of State Variables
	TreeMap configsMap = getParamStoredMap()
	configsMap[paramNum] = value
	configsList[device.id][paramNum] = value
	//device.updateDataValue("configVals", configsMap.inspect())
}

Map getParamStoredMap() {
	TreeMap configsMap = configsList[device.id]
	if (configsMap == null) {
		configsMap = [:]
		if (device.getDataValue("configVals")) {
			try {
				configsMap = evaluate(device.getDataValue("configVals"))
			}
			catch(Exception e) {
				logWarn("Clearing Invalid configVals: ${e}")
				device.removeDataValue("configVals")
			}
		}
		configsList[device.id] = configsMap
	}
	return configsMap
}

//Parameter List Functions
//This will rebuild the list for the current model and firmware only as needed
//paramsList Structure: MODEL:[FIRMWARE:PARAM_MAPS]
//PARAM_MAPS [num, name, title, description, size, defaultVal, options, firmVer]
@Field static Map<String, Map<String, List>> paramsList = new java.util.concurrent.ConcurrentHashMap()
void updateParamsList() {
	logDebug "Update Params List"
	String devModel = state.deviceModel
	Short modelNum = deviceModelShort
	Short modelSeries = Math.floor(modelNum/10)
	BigDecimal firmware = firmwareVersion

	List<Map> tmpList = []
	paramsMap.each { name, pMap ->
		Map tmpMap = pMap.clone()
		if (tmpMap.options) tmpMap.options = tmpMap.options?.clone()
		if (tmpMap.range) tmpMap.range = (tmpMap.range).toString()

		//Save the name
		tmpMap.name = name

		//Apply custom adjustments
		tmpMap.changes.each { m, changes ->
			if (m == devModel || m == modelNum || m ==~ /${modelSeries}X/) {
				tmpMap.putAll(changes)
				if (changes.options) { tmpMap.options = changes.options.clone() }
			}
		}
		tmpMap.changesFR.each { m, changes ->
			if (firmware >= m.getFrom() && firmware <= m.getTo()) {
				tmpMap.putAll(changes)
				if (changes.options) { tmpMap.options = changes.options.clone() }
			}
		}
		//Don't need this anymore
		tmpMap.remove("changes")
		tmpMap.remove("changesFR")

		//Set DEFAULT tag on the default
		tmpMap.options.each { k, val ->
			if (k == tmpMap.defaultVal) {
				tmpMap.options[(k)] = "${val} [DEFAULT]"
			}
		}

		//Save to the temp list
		tmpList << tmpMap
	}

	//Remove invalid or not supported by firmware
	tmpList.removeAll { it.num == null }
	tmpList.removeAll { firmware < (it.firmVer ?: 0) }
	tmpList.removeAll {
		if (it.firmVerM) {
			(firmware-(int)firmware)*100 < it.firmVerM[(int)firmware]
		}
	}

	//Save it to the static list
	if (paramsList[devModel] == null) paramsList[devModel] = [:]
	paramsList[devModel][firmware] = tmpList
}

//Verify the list and build if its not populated
void verifyParamsList() {
	String devModel = state.deviceModel
	BigDecimal firmware = firmwareVersion
	if (!paramsMap.settings?.fixed) fixParamsMap()
	if (paramsList[devModel] == null) updateParamsList()
	if (paramsList[devModel][firmware] == null) updateParamsList()
}

//Gets full list of params
List<Map> getConfigParams() {
	//logDebug "Get Config Params"
	if (!device) return []
	String devModel = state.deviceModel
	BigDecimal firmware = firmwareVersion

	//Try to get device model if not set
	if (devModel) { verifyParamsList() }
	else          { runInMillis(200, setDevModel) }
	//Bail out if unknown device
	if (!devModel || devModel == "UNK00") return []

	return paramsList[devModel][firmware]
}

//Get a single param by name or number
Map getParam(String search) {
	verifyParamsList()
	return configParams.find{ it.name == search }
}
Map getParam(Number search) {
	verifyParamsList()
	return configParams.find{ it.num == search }
}

//Convert Param Value if Needed
BigDecimal getParamValue(String paramName) {
	return getParamValue(getParam(paramName))
}
BigDecimal getParamValue(Map param) {
	if (param == null) return
	BigDecimal paramVal = safeToDec(settings."configParam${param.num}", param.defaultVal)

	//Reset hidden parameters to default
	if (param.hidden && settings."configParam${param.num}" != null) {
		logWarn "Resetting hidden parameter ${param.name} (${param.num}) to default ${param.defaultVal}"
		device.removeSetting("configParam${param.num}")
		paramVal = param.defaultVal
	}

	return paramVal
}

/*** Preference Helpers ***/
String fmtTitle(String str) {
	return "<strong>${str}</strong>"
}
String fmtDesc(String str) {
	return "<div style='font-size: 85%; font-style: italic; padding: 1px 0px 4px 2px;'>${str}</div>"
}
String fmtHelpInfo(String str) {
	String info = "${DRIVER} v${VERSION}"
	String prefLink = "<a href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 70%;'>${info}</div></a>"
	String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid Crimson; border-radius: 6px;'" //SlateGray
	String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}

private getTimeOptionsRange(String name, Integer multiplier, List range) {
	return range.collectEntries{ [(it*multiplier): "${it} ${name}${it == 1 ? '' : 's'}"] }
}

/*** Other Helper Functions ***/
void updateSyncingStatus(Integer delay=2) {
	runIn(delay, refreshSyncStatus)
	sendEvent(name:"syncStatus", value:"Syncing...")
}

void refreshSyncStatus() {
	Integer changes = pendingChanges
	sendEvent(name:"syncStatus", value:(changes ? "${changes} Pending Changes" : "Synced"))
	device.updateDataValue("configVals", getParamStoredMap()?.inspect())
}

void updateLastCheckIn() {
	def nowDate = new Date()
	state.lastCheckInDate = convertToLocalTimeString(nowDate)

	Long lastExecuted = state.lastCheckInTime ?: 0
	Long allowedMil = 24 * 60 * 60 * 1000   //24 Hours
	if (lastExecuted + allowedMil <= nowDate.time) {
		state.lastCheckInTime = nowDate.time
		if (lastExecuted) runIn(4, doCheckIn)
		scheduleCheckIn()
	}
}

void scheduleCheckIn() {
	def cal = Calendar.getInstance()
	cal.add(Calendar.MINUTE, -1)
	Integer hour = cal[Calendar.HOUR_OF_DAY]
	Integer minute = cal[Calendar.MINUTE]
	schedule( "0 ${minute} ${hour} * * ?", doCheckIn)
}

void doCheckIn() {
	String devModel = (state.deviceModel ?: "NA") + (state.subModel ? ".${state.subModel}" : "")
	String checkUri = "http://jtp10181.gateway.scarf.sh/${DRIVER}/chk-${devModel}-v${VERSION}"

	try {
		httpGet(uri:checkUri, timeout:4) { logDebug "Driver ${DRIVER} ${devModel} v${VERSION}" }
		state.lastCheckInTime = (new Date()).time
	} catch (Exception e) { }
}

Integer getPendingChanges() {
	Integer configChanges = configParams.count { param ->
		Integer paramVal = getParamValueAdj(param)
		((paramVal != null) && (paramVal != getParamStoredValue(param.num)))
	}
	Integer pendingAssocs = Math.ceil(getConfigureAssocsCmds()?.size()/2) ?: 0
	return (!state.resyncAll ? (configChanges + pendingAssocs) : configChanges)
}

//iOS app has no way of clearing string input so workaround is to have users enter 0.
String getAssocDNIsSetting(grp) {
	String val = settings."assocDNI$grp"
	return ((val && (val.trim() != "0")) ? val : "")
}

List getAssocDNIsSettingNodeIds(grp) {
	String dni = getAssocDNIsSetting(grp)
	List nodeIds = convertHexListToIntList(dni.split(","))

	if (dni && !nodeIds) {
		logWarn "'${dni}' is not a valid value for the 'Device Associations - Group ${grp}' setting.  All z-wave devices have a 2 character Device Network ID and if you're entering more than 1, use commas to separate them."
	}
	else if (nodeIds.size() > maxAssocNodes) {
		logWarn "The 'Device Associations - Group ${grp}' setting contains more than ${maxAssocNodes} IDs so some (or all) may not get associated."
	}

	return nodeIds
}

//Used with configure to reset variables
void clearVariables() {
	logWarn "Clearing state variables and data..."

	//Backup
	String devModel = state.deviceModel
	def engTime = state.energyTime

	//Clears State Variables
	state.clear()

	//Clear Config Data
	configsList["${device.id}"] = [:]
	device.removeDataValue("configVals")
	//Clear Data from other Drivers
	device.removeDataValue("zwaveAssociationG1")
	device.removeDataValue("zwaveAssociationG2")
	device.removeDataValue("zwaveAssociationG3")

	//Restore
	if (devModel) state.deviceModel = devModel
	if (engTime) state.energyTime = engTime
	state.resyncAll = true
}

//Stash the model in a state variable
String setDevModel(BigDecimal firmware) {
	if (!device) return
	def devTypeId = convertIntListToHexList([safeToInt(device.getDataValue("deviceType")),safeToInt(device.getDataValue("deviceId"))],4)
	String devModel = deviceModelNames[devTypeId.join(":")] ?: "UNK00"
	if (!firmware) { firmware = firmwareVersion }

	state.deviceModel = devModel
	device.updateDataValue("deviceModel", devModel)
	logDebug "Set Device Info - Model: ${devModel} | Firmware: ${firmware}"

	if (devModel == "UNK00") {
		logWarn "Unsupported Device USE AT YOUR OWN RISK: ${devTypeId}"
		state.WARNING = "Unsupported Device Model - USE AT YOUR OWN RISK!"
	}
	else state.remove("WARNING")

	//Setup parameters if not set
	verifyParamsList()

	return devModel
}

Integer getDeviceModelShort() {
	return safeToInt(state.deviceModel?.drop(3))
}

BigDecimal getFirmwareVersion() {
	String version = device?.getDataValue("firmwareVersion")
	return ((version != null) && version.isNumber()) ? version.toBigDecimal() : 0.0
}

Boolean isLongRange() {
	return ((device?.deviceNetworkId as Integer) > 255)
}

String convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	} else {
		return "$dt"
	}
}

List convertIntListToHexList(intList, pad=2) {
	def hexList = []
	intList?.each {
		hexList.add(Integer.toHexString(it).padLeft(pad, "0").toUpperCase())
	}
	return hexList
}

List convertHexListToIntList(String[] hexList) {
	def intList = []

	hexList?.each {
		try {
			it = it.trim()
			intList.add(Integer.parseInt(it, 16))
		}
		catch (e) { }
	}
	return intList
}

Integer validateRange(val, Integer defaultVal, Integer lowVal, Integer highVal) {
	Integer intVal = safeToInt(val, defaultVal)
	if (intVal > highVal) {
		return highVal
	} else if (intVal < lowVal) {
		return lowVal
	} else {
		return intVal
	}
}

Integer safeToInt(val, defaultVal=0) {
	if ("${val}"?.isInteger())		{ return "${val}".toInteger() }
	else if ("${val}"?.isNumber())	{ return "${val}".toDouble()?.round() }
	else { return defaultVal }
}

BigDecimal safeToDec(val, defaultVal=0, roundTo=-1) {
	BigDecimal decVal = "${val}"?.isNumber() ? "${val}".toBigDecimal() : defaultVal
	if (roundTo == 0)		{ decVal = Math.round(decVal) }
	else if (roundTo > 0)	{ decVal = decVal.setScale(roundTo, BigDecimal.ROUND_HALF_UP).stripTrailingZeros() }
	if (decVal.scale()<0)	{ decVal = decVal.setScale(0) }
	return decVal
}

Boolean isDuplicateCommand(Long lastExecuted, Long allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}


/*******************************************************************
 ***** Logging Functions
********************************************************************/
//Logging Level Options
@Field static final Map LOG_LEVELS = [0:"Error", 1:"Warn", 2:"Info", 3:"Debug", 4:"Trace"]
@Field static final Map LOG_TIMES = [0:"Indefinitely", 30:"30 Minutes", 60:"1 Hour", 120:"2 Hours", 180:"3 Hours", 360:"6 Hours", 720:"12 Hours", 1440:"24 Hours"]

/*//Command to set log level, OPTIONAL. Can be copied to driver or uncommented here
command "setLogLevel", [ [name:"Select Level*", description:"Log this type of message and above", type: "ENUM", constraints: LOG_LEVELS],
	[name:"Debug/Trace Time", description:"Timer for Debug/Trace logging", type: "ENUM", constraints: LOG_TIMES] ]
*/

//Additional Preferences
preferences {
	//Logging Options
	input name: "logLevel", type: "enum", title: fmtTitle("Logging Level"),
		description: fmtDesc("Logs selected level and above"), defaultValue: 3, options: LOG_LEVELS
	input name: "logLevelTime", type: "enum", title: fmtTitle("Logging Level Time"),
		description: fmtDesc("Time to enable Debug/Trace logging"),defaultValue: 30, options: LOG_TIMES
	//Help Link
	input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
}

//Call this function from within updated() and configure() with no parameters: checkLogLevel()
void checkLogLevel(Map levelInfo = [level:null, time:null]) {
	unschedule(logsOff)
	//Set Defaults
	if (settings.logLevel == null) {
		device.updateSetting("logLevel",[value:"3", type:"enum"])
		levelInfo.level = 3
	}
	if (settings.logLevelTime == null) {
		device.updateSetting("logLevelTime",[value:"30", type:"enum"])
		levelInfo.time = 30
	}
	//Schedule turn off and log as needed
	if (levelInfo.level == null) levelInfo = getLogLevelInfo()
	String logMsg = "Logging Level is: ${LOG_LEVELS[levelInfo.level]} (${levelInfo.level})"
	if (levelInfo.level >= 3 && levelInfo.time > 0) {
		logMsg += " for ${LOG_TIMES[levelInfo.time]}"
		runIn(60*levelInfo.time, logsOff)
	}
	logInfo(logMsg)

	//Store last level below Debug
	if (levelInfo.level <= 2) state.lastLogLevel = levelInfo.level
}

//Function for optional command
void setLogLevel(String levelName, String timeName=null) {
	Integer level = LOG_LEVELS.find{ levelName.equalsIgnoreCase(it.value) }.key
	Integer time = LOG_TIMES.find{ timeName.equalsIgnoreCase(it.value) }.key
	device.updateSetting("logLevel",[value:"${level}", type:"enum"])
	checkLogLevel(level: level, time: time)
}

Map getLogLevelInfo() {
	Integer level = settings.logLevel != null ? settings.logLevel as Integer : 1
	Integer time = settings.logLevelTime != null ? settings.logLevelTime as Integer : 30
	return [level: level, time: time]
}

//Legacy Support
void debugLogsOff() {
	device.removeSetting("logEnable")
	device.updateSetting("debugEnable",[value:false, type:"bool"])
}

//Current Support
void logsOff() {
	logWarn "Debug and Trace logging disabled..."
	if (logLevelInfo.level >= 3) {
		Integer lastLvl = state.lastLogLevel != null ? state.lastLogLevel as Integer : 2
		device.updateSetting("logLevel",[value:lastLvl.toString(), type:"enum"])
		logWarn "Logging Level is: ${LOG_LEVELS[lastLvl]} (${lastLvl})"
	}
}

//Logging Functions
void logErr(String msg) {
	log.error "${device.displayName}: ${msg}"
}
void logWarn(String msg) {
	if (logLevelInfo.level>=1) log.warn "${device.displayName}: ${msg}"
}
void logInfo(String msg) {
	if (logLevelInfo.level>=2) log.info "${device.displayName}: ${msg}"
}
void logDebug(String msg) {
	if (logLevelInfo.level>=3) log.debug "${device.displayName}: ${msg}"
}
void logTrace(String msg) {
	if (logLevelInfo.level>=4) log.trace "${device.displayName}: ${msg}"
}
