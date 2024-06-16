/*
 *  Zooz ZAC36 Titan Valve Actuator
 *    - Model: ZAC36 - MINIMUM FIRMWARE 1.10
 *
 *  For Support, Information, and Updates:
 *  https://community.hubitat.com/t/zooz-zac36/79426
 *  https://github.com/jtp10181/Hubitat/tree/main/Drivers/zooz
 *

Changelog:

## [1.0.4] - 2024-06-16 (@jtp10181)
  - Update library and common code

## [1.0.2] - 2024-03-01 (@jtp10181)
  - Update library code
  - Add parameters 113, 114 and 115 for battery configuration
  - Add support for battery v2 disconnected/charging status
  - Fixed refresh setting wrong state, removed valve notification from refresh (uses switch state)
  - Changed battery and powersource reporting defaults to enabled
  - Changed invert switch default to be disabled (per recommendation from Zooz)

## [1.0.0] - 2023-11-11 (@jtp10181)
  - Code refactor to new code base and library
  - Fixed on/off commands to follow Zooz docs
  - Updated all event senders to log (debug) unknown events
  - Added setParamater command
  - Added new paramaters for firmware 1.15/1.19
  - Added battery and powerSource capabilities
  - Added support for power and battery notifications
  - Fixed Temp Units setting so it converts the defaults and settings from F<>C
  - Removed supervision Encapsulation code (not fully working)
  - Settings verbiage cleanup and clarification

## [0.2.0] - 2023-08-11 (@jtp10181)
  - Minor fixes

## [0.1.0] - 2021-09-12 (@jtp10181)
  ### Added
  - Initial Release, supports all known settings and features except associations

NOTICE: This file has been created by *Jeff Page* with some code used 
	from the original work of *Zooz* under compliance with the Apache 2.0 License.

Below link is for original source (Kevin LaFramboise @krlaframboise)
https://github.com/krlaframboise/SmartThings/tree/master/devicetypes/zooz/zooz-zac36-titan-valve-actuator.src

 *  Copyright 2021-2023 Jeff Page
 *  Copyright 2021 Zooz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/

import groovy.transform.Field

@Field static final String VERSION = "1.0.4"
@Field static final String DRIVER = "Zooz-ZAC36"
@Field static final String COMM_LINK = "https://community.hubitat.com/t/zooz-zac36/79426"
@Field static final Map deviceModelNames = ["0101:0036":"ZAC36"]

metadata {
	definition (
		name: "Zooz ZAC36 Titan Valve Actuator",
		namespace: "jtp10181",
		author: "Jeff Page (@jtp10181)",
		importUrl: "https://raw.githubusercontent.com/jtp10181/Hubitat/main/Drivers/zooz/zooz-zac36-valve-actuator.groovy"
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		capability "Valve"
		capability "WaterSensor"
		capability "TemperatureMeasurement"
		capability "Battery"
		capability "PowerSource"
		capability "Configuration"
		capability "Refresh"

		//command "refreshParams"

		command "setParameter",[[name:"parameterNumber*",type:"NUMBER", description:"Parameter Number"],
			[name:"value*",type:"NUMBER", description:"Parameter Value"],
			[name:"size",type:"NUMBER", description:"Parameter Size"]]

		//DEBUGGING
		//command "debugShowVars"

		attribute "syncStatus", "string"
		attribute "temperatureAlarm", "string"
		attribute "batteryStatus", "string"

		fingerprint mfr:"027A", prod:"0101", deviceId: "0036", inClusters:"0x00,0x00", controllerType: "ZWV" //Zooz ZAC36 Titan Valve Actuator
	}

	preferences {

		input name: "tempUnits", type: "enum",
			title: fmtTitle("Temperature Units for Settings:"),
			description: fmtDesc("WARNING: When changed this will convert existing settings"),
			defaultValue: temperatureScale == "F" ? 1 : 0,
			options: [0:"Celsius (°C)", 1:"Fahrenheit (°F)"]

		configParams.each { param ->
			if (!param.hidden) {
				Integer paramVal = getParamValue(param)
				if (param.options) {
					input "configParam${param.num}", "enum",
						title: fmtTitle("${param.title}"),
						description: fmtDesc("• Parameter #${param.num}, Selected: ${paramVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
						defaultValue: paramVal,
						options: param.options,
						required: false
				}
				else if (param.range) {
					input "configParam${param.num}", "number",
						title: fmtTitle("${param.title}"),
						description: fmtDesc("• Parameter #${param.num}, Range: ${(param.range).toString()}, DEFAULT: ${param.defaultVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
						defaultValue: paramVal,
						range: param.range,
						required: false
				}
			}
		}

		// for(int i in 2..maxAssocGroups) {
		// 	input "assocDNI$i", "string",
		// 		title: fmtTitle("Device Associations - Group $i"),
		// 		description: fmtDesc("Supports up to ${maxAssocNodes} Hex Device IDs separated by commas. Check device documentation for more info. Save as blank or 0 to clear."),
		// 		required: false
		// }
	}
}

void debugShowVars() {
	log.warn "settings ${settings.hashCode()} ${settings}"
	log.warn "paramsList ${paramsList.hashCode()} ${paramsList}"
	log.warn "paramsMap ${paramsMap.hashCode()} ${paramsMap}"
}

//Association Settings
@Field static final int maxAssocGroups = 1
@Field static final int maxAssocNodes = 5

/*** Static Lists and Settings ***/
@Field static int fahrenheitHB = 0x01
@Field static int negativeHB = 0x10
//Sensor Types
@Field static int tempSensor = 0x01
//Notification Types
@Field static int heatAlarm = 0x04
@Field static int waterAlarm = 0x05
@Field static int powerManagement = 0x08
@Field static int waterValve = 0x0F
//Other / General
@Field static Map chargeStatus = [0x00:"discharging", 0x01:"charging", 0x02:"maintaining"]


//Main Parameters Listing
@Field static Map<String, Map> paramsMap =
[
	tempThreshold: [ num:34, 
		title: "Temperature Reporting Change Trigger (°)",
		size: 2, defaultVal: 4, 
		range: "0..255", hB: true
	],
	tempOffset: [ num:35, 
		title: "Temperature Sensor Offset (°)", 
		size: 2, defaultVal: 0,
		range: "-255..255", hB: true
	],
	tempInterval: [ num:45, 
		title: "Temperature Detection Interval (minutes)",
		size: 1, defaultVal: 15, 
		range: "1..60",
		firmVer: 1.13
	],
	overheatAlarm: [ num:36, 
		title: "Overheat Alarm Trigger (°C/F)", 
		size: 2, defaultF: 104, 
		range: "0..255", hB: true
	],
	overheatCancel: [ num:37, 
		title: "Overheat Cancellation Trigger (°C/F)", 
		size: 2, defaultF: 86, 
		range: "0..255", hB: true
	],
	freezeAlarm: [ num:40, 
		title: "Freeze Alarm Trigger (°C/F)", 
		size: 2, defaultF: 32, 
		range: "0..255", hB: true
	],
	freezeCancel: [ num:41, 
		title: "Freeze Cancellation Trigger (°C/F)", 
		size: 2, defaultF: 36, 
		range: "0..255", hB: true
	],
	freezeControl: [ num:42, 
		title: "Valve Control during Freeze Alarm",
		description: "Disabled prevents ANY valve movement during a freeze alarm",
		size: 1, defaultVal: 1, 
		options: [1:"Allowed", 0:"Disabled"]
	],
	leakControl: [ num:51, 
		title: "Valve Auto Shut-Off when Leak Detected", 
		description: "Disabling will also stop leak probe reports",
		size: 1, defaultVal: 1, 
		options: [1:"Enabled", 0:"Disabled"]
	],
	soundAlarm: [ num:65, 
		title: "Sound Alarm and Notifications", 
		size: 1, defaultVal: 1, 
		options: [1:"Enabled", 0:"Disabled"]
	],
	ledBrightness: [ num:66, 
		title: "LED Indicator Brightness %", 
		size: 1, defaultVal: 80, 
		range: "0..99"
	],
	keylockProtection: [num:67, 
		title:"Z-Wave Button Lock",
		description: "When enabled the button no longer controls the valve",
		size: 1, defaultVal: 0, 
		options: [0:"Disabled", 1:"Enabled"]
	],
	testMode: [ num:97, 
		title: "Auto Test Mode",
		description:"Valve will make 1/8 turn to test operation",
		size: 1, defaultVal: 3, 
		options: [3:"Always Enabled", 0:"Disabled"]
	],
	testFrequency: [ num:98, 
		title: "Auto Test Frequency (days)",
		size: 1, defaultVal: 14, 
		range: "1..30"
	],
	leakReports: [num:84, 
		title:"Leak Sensor Reports", 
		size: 1, defaultVal: 1,
		options: [1:"Enabled", 0:"Disabled"],
		firmVer: 1.19
	],
	powerReports: [num:85, 
		title:"Power Source Reports", 
		size: 1, defaultVal: 1,
		options: [1:"Enabled", 0:"Disabled"],
		firmVer: 1.19
	],
	batteryReports: [num:86, 
		title:"Battery Reports", 
		size: 1, defaultVal: 1,
		options: [1:"Enabled", 0:"Disabled"],
		firmVer: 1.19
	],
	batteryThreshold: [ num:113,
		title: "Battery Threshold Change",
		description: "Report battery level when changed by this %",
		size: 1, defaultVal: 10,
		range: "0..100",
		firmVer: 1.19
	],
	batteryAlarm: [ num:114,
		title: "Low Battery Report Level",
		size: 1, defaultVal: 30,
		range: "0..100",
		firmVer: 1.19
	],
	batteryControl: [ num:115,
		title: "Low Battery Auto-Close",
		description: "Automatically close the valve when low battery is triggered",
		size: 1, defaultVal: 1,
		options: [1:"Enabled", 0:"Disabled"],
		firmVer: 1.19
	],
	openOffset: [ num:99, 
		title: "Manual Calibration Offset (Open Angle %)", 
		size: 1, defaultVal: 9, 
		range: "1..10",
		firmVer: 1.15
	],
	closeOffset: [ num:100, 
		title: "Manual Calibration Offset (Close Angle %)", 
		size: 1, defaultVal: 4, 
		range: "1..10",
		firmVer: 1.15
	],
	inverseReport: [num:17, 
		title:"Inverse Switch Report", 
		description: "When enabled off=open, on=closed",
		size: 1, defaultVal: 0,
		options: [0:"Disabled", 1:"Enabled"],
		hidden: false
	],
	valveReports: [num:81, 
		title:"Open / Close Reports", 
		size: 1, defaultVal: 1,
		options: [1:"Enabled", 0:"Disabled"],
		firmVer: 1.19,
		hidden: true
	],
]

/* ZAC36 v1.14
CommandClassReport - class:0x22, version:1   (Application Status)
CommandClassReport - class:0x25, version:2   (Binary Switch)
CommandClassReport - class:0x31, version:11   (Multilevel Sensor)
CommandClassReport - class:0x55, version:2   (Transport Service)
CommandClassReport - class:0x59, version:3   (Association Group Information (AGI))
CommandClassReport - class:0x5A, version:1   (Device Reset Locally)
CommandClassReport - class:0x5E, version:2   (Z-Wave Plus Info)
CommandClassReport - class:0x6C, version:1   (Supervision)
CommandClassReport - class:0x70, version:4   (Configuration)
CommandClassReport - class:0x71, version:8   (Notification)
CommandClassReport - class:0x72, version:2   (Manufacturer Specific)
CommandClassReport - class:0x73, version:1   (Powerlevel)
CommandClassReport - class:0x7A, version:5   (Firmware Update Meta Data)
CommandClassReport - class:0x80, version:1   (Battery)
CommandClassReport - class:0x85, version:3   (Association)
CommandClassReport - class:0x86, version:3   (Version)
CommandClassReport - class:0x87, version:3   (Indicator)
CommandClassReport - class:0x8E, version:4   (Multi Channel Association)
CommandClassReport - class:0x98, version:1   (Security 0)
CommandClassReport - class:0x9F, version:1   (Security 2)
*/

@Field static final Map commandClassVersions = [
	0x25: 1,	// Switch Binary
	0x6C: 1,	// Supervision
	0x70: 1,	// Configuration
	0x71: 8,	// Notification
	0x72: 2,	// ManufacturerSpecific
	0X80: 2,	// Battery
	0x85: 2,	// Association
	0x86: 2,	// Version
	0x8E: 3,	// Multi Channel Association
]


/*******************************************************************
 ***** Core Functions
********************************************************************/
void installed() {
	logWarn "installed..."
	initialize()
}

void initialize() {
	logWarn "initialize..."
	refresh()
}

void configure() {
	logWarn "configure..."

	if (!pendingChanges || state.resyncAll == null) {
		logDebug "Enabling Full Re-Sync"
		clearVariables()
		state.resyncAll = true
	}

	updateSyncingStatus(6)
	runIn(1, executeRefreshCmds)
	runIn(5, executeConfigureCmds)
}

void updated() {
	logDebug "updated..."

	if (!getParamValue("leakReports")) {
		device.deleteCurrentState("water")
	}
	if (!getParamValue("batteryReports")) {
		device.deleteCurrentState("battery")
		device.deleteCurrentState("batteryStatus")
	}
	if (!getParamValue("powerReports")) {
		device.deleteCurrentState("powerSource")
	}
	if ((tempUnits.toInteger() ? "F" : "C") != state.tempUnits) {
		logDebug "Setting Temperature Defaults for Preferences"
		paramsList = [:]
		paramsMap['settings'] = [:]
		verifyParamsList()

		if (state.tempUnits != null) {
			logDebug "Converting Temperature Settings to new Units"
			["overheatAlarm","overheatCancel","freezeAlarm","freezeCancel"].each {
				Map param = getParam(it)
				Integer paramVal = getParamValue(param)
				Integer newValue = (tempUnits as Integer) ? celsiusToFahrenheit(paramVal) : fahrenheitToCelsius(paramVal)
				device.updateSetting("configParam${param.num}",[value:newValue, type:"number"])
			}
		}

		//Save the new setting for comparison
		state.tempUnits = (tempUnits.toInteger() ? "F" : "C")
	}

	runIn(1, executeConfigureCmds)
}

void refresh() {
	logDebug "refresh..."
	executeRefreshCmds()
}


/*******************************************************************
 ***** Driver Commands
********************************************************************/
/*** Capabilities ***/
def on() {
	logDebug "on..."
	state.pendingDigital = true
	runIn(30, switchDigitalRemove)
	return switchBinarySetCmd(0xFF)
}

def off() {
	logDebug "off..."
	state.pendingDigital = true
	runIn(30, switchDigitalRemove)
	return switchBinarySetCmd(0x00)
}

def close() {
	logDebug "close..."
	Integer inverse = getParamValue("inverseReport")
	return (inverse ? on() : off())
}

def open() {
	logDebug "open..."
	Integer inverse = getParamValue("inverseReport")
	return (inverse ? off() : on())
}


/*** Custom Commands ***/
void refreshParams() {
	List<String> cmds = []
	for (int i = 1; i <= maxAssocGroups; i++) {
		cmds << associationGetCmd(i)
	}

	configParams.each { param ->
		cmds << configGetCmd(param)
	}

	if (cmds) sendCommands(cmds)
}

def setParameter(paramNum, value, size = null) {
	paramNum = safeToInt(paramNum)
	Map param = getParam(paramNum)
	if (param && !size) { size = param.size	}

	if (paramNum == null || value == null || size == null) {
		logWarn "Incomplete parameter list supplied..."
		logWarn "Syntax: setParameter(paramNum, value, size)"
		return
	}
	logDebug "setParameter ( number: $paramNum, value: $value, size: $size )" + (param ? " [${param.name} - ${param.title}]" : "")
	return configSetGetCmd([num: paramNum, size: size], value as Integer)
}

/*******************************************************************
 ***** Z-Wave Reports
********************************************************************/
void parse(String description) {
	zwaveParse(description)
}
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	zwaveMultiChannel(cmd)
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

		//Convert Temp / Negatives for ZAC36 as needed
		String displayVal = cmd.scaledConfigurationValue.toString()
		if (param.hB) {
			Integer hB = cmd.configurationValue[0]
			Integer adjVal = cmd.configurationValue[1]
			//logDebug "${cmd.configurationValue} | ${hB} | ${adjVal} | ${(hB & 0x01)}"
			//Check if temp units HighByte matches settings
			if ((hB & fahrenheitHB) != tempUnits.toInteger()) {
				logWarn "${param.name} (#${param.num}) returned value does not match configured temperature units. Run CONFIGURE to correct all parameters!"
			}
			//Check for negative HighByte and adjust if needed
			if (hB & negativeHB) {
				adjVal = -1 * adjVal
			}
			displayVal = "${cmd.configurationValue}  ${adjVal.toString()}${(hB & 0x01)?'°F':'°C'}"
		}
		logDebug "${param.name} (#${param.num}) = ${displayVal}"
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

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"
	sendSwitchEvents(cmd.value, "basic")
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"
	sendSwitchEvents(cmd.value, "binary")
}

void zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd, ep=0) {
	logDebug "${cmd} (ep ${ep})"	
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"	
	switch (cmd.sensorType) {
		case tempSensor:
			def temp = convertTemperatureIfNeeded(cmd.scaledSensorValue, (cmd.scale ? "F" : "C"), cmd.precision)			
			sendEventLog(name:"temperature", value:temp, unit:temperatureScale)
			break
		default:
			logDebug "Unhandled: ${cmd}"
	}
}

void zwaveEvent(hubitat.zwave.commands.batteryv2.BatteryReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"

	String battStatus = (cmd.disconnected ? "disconnected" : "connected")
	if (cmd.disconnected == false) {
		battStatus = chargeStatus[(int) cmd.chargingStatus] ?: "unknown"
	}
	sendEventLog(name:"batteryStatus", value:battStatus, desc:"battery is ${battStatus}")

	Integer batLvl = cmd.batteryLevel
	if (batLvl == 0xFF) {
		batLvl = 1
		logWarn "LOW BATTERY WARNING"
	}

	batLvl = validateRange(batLvl, 100, 0, 100)
	String descText = "battery level is ${batLvl}%"
	sendEventLog(name:"battery", value:batLvl, unit:"%", desc:descText, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, ep=0) {
	logTrace "${cmd} (ep ${ep})"
	switch (cmd.notificationType as Integer) {
		case heatAlarm:
			sendHeatAlarmEvent(cmd.event, cmd.eventParameter[0])
			break
		case waterAlarm:
			sendWaterAlarmEvent(cmd.event, cmd.eventParameter[0])
			break
		case waterValve:
			sendValveEvent(cmd.event, cmd.eventParameter[0])
			break
		case powerManagement:
			sendPowerEvent(cmd.event, cmd.eventParameter[0])
			break
		default:
			logDebug "Unhandled: ${cmd}"
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

//rawVal Default: 0 = Open (off), 0xFF = Closed (on), 0xFE = Unknown / Stuck
void sendSwitchEvents(rawVal, String type, Integer ep=0) {
	if (rawVal == 0x00 || rawVal == 0xFF) {
		sendEventLog(name:"switch", value:(rawVal ? "on":"off"), type:type, ep)

		//Also send open/close to be safe
		Integer inverse = getParamValue("inverseReport")
		int valveVal = (rawVal ? 1 : 0) ^ inverse //XOR flips the bit if inverse
		sendEventLog(name:"valve", value:(valveVal ? "open":"closed"), type:type, ep)
	}
	else {
		sendEventLog(name:"valve", value:"working", type:type, ep)
	}
}

//parameter[0] 0x01 = Open, 0x00 = Closed
void sendValveEvent(Integer event, Integer parameter) {
	switch (event as Integer) {
		case 0x01:  //Valve operation
			String type = (state.pendingDigital ? "digital" : "physical")
			sendEventLog(name:"valve", value:(parameter ? "open":"closed"), type:type)
			switchDigitalRemove()
			break
		default:
			logDebug "Unhandled Valve Event: ${event}, ${parameter}"
	}
}
void switchDigitalRemove() {
	state.remove("pendingDigital")
}

void sendHeatAlarmEvent(Integer event, Integer parameter) {
	switch (event as Integer) {
		case 0x00:  //Idle
			sendEventLog(name:"temperatureAlarm", value:"normal")
			break
		case [0x01, 0x02]:  //Overheat detected
			sendEventLog(name:"temperatureAlarm", value:"high")
			break
		case [0x05, 0x06]:  //Under heat detected
			sendEventLog(name:"temperatureAlarm", value:"low")
			break
		default:			
			logDebug "Unhandled Heat Alarm: ${event}, ${parameter}"
	}
}

void sendWaterAlarmEvent(Integer event, Integer parameter) {
	switch (event as Integer) {
		case 0x00:  //Idle
			sendEventLog(name:"water", value:"dry")
			break
		case [0x01, 0x02]:  //Water leak detected
			sendEventLog(name:"water", value:"wet")
			break
		default:
			logDebug "Unhandled Water Alarm: ${event}, ${parameter}"
	}
}

void sendPowerEvent(Integer event, Integer parameter) {
	switch (event as Integer) {
		case 0x00: break  //Idle State - ignored
		case 0x02:  //AC mains disconnected
			sendEventLog(name:"powerSource", value:"battery")
			break
		case 0x03:  //AC mains re-connected
			sendEventLog(name:"powerSource", value:"mains")
			break
		default:
			logDebug "Unhandled Power Management: ${event}, ${parameter}"
	}
}


/*******************************************************************
 ***** Execute / Build Commands
********************************************************************/
void executeConfigureCmds() {
	logDebug "executeConfigureCmds..."

	//Checks and sets scheduled turn off
	checkLogLevel()

	List<String> cmds = []

	if (!firmwareVersion || !state.deviceModel) {
		cmds << versionGetCmd()
	}

	//Set Temp Reporting based on hub temp scale setting
	cmds += configSetGetCmd([num:33,size:1], temperatureScale == "F" ? 2 : 1)

	cmds += getConfigureAssocsCmds(true)

	configParams.each { param ->
		Integer paramVal = getParamValueAdj(param)
		Integer storedVal = getParamStoredValue(param.num)

		if ((paramVal != null) && (state.resyncAll || (storedVal != paramVal))) {
			logDebug "Changing ${param.name} (#${param.num}) from ${storedVal} to ${paramVal}"
			cmds += configSetGetCmd(param, paramVal)
		}
	}

	state.resyncAll = false

	if (cmds) sendCommands(cmds)
}

void executeRefreshCmds() {
	List<String> cmds = []

	if (state.resyncAll || !firmwareVersion || !state.deviceModel) {
		cmds << mfgSpecificGetCmd()
		cmds << versionGetCmd()
	}

	cmds << switchBinaryGetCmd() //Switch
	cmds << sensorMultilevelGetCmd(tempSensor)  //Temperature
	// cmds << notificationGetCmd(waterValve, 0x01)  //Valve  -- THIS DOES NOT WORK CORRECTLY
	cmds << notificationGetCmd(heatAlarm, 0x00)
	if (getParamValue("leakReports")) { cmds << notificationGetCmd(waterAlarm, 0x00) }

	//Battery and Power
	if (getParamValue("batteryReports")) { cmds << batteryGetCmd() }
	if (getParamValue("powerReports")) {
		cmds << notificationGetCmd(powerManagement, 0x02)
		cmds << notificationGetCmd(powerManagement, 0x03)
	}

	sendCommands(cmds,300)
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


/*******************************************************************
 ***** Required for Library
********************************************************************/
//These have to be added in after the fact or groovy complains
void fixParamsMap() {
	paramsMap.overheatAlarm.defaultVal = fahrenheitToCelsiusIfNeeded(paramsMap.overheatAlarm.defaultF)
	paramsMap.overheatCancel.defaultVal = fahrenheitToCelsiusIfNeeded(paramsMap.overheatCancel.defaultF)
	paramsMap.freezeAlarm.defaultVal = fahrenheitToCelsiusIfNeeded(paramsMap.freezeAlarm.defaultF)
	paramsMap.freezeCancel.defaultVal = fahrenheitToCelsiusIfNeeded(paramsMap.freezeCancel.defaultF)
	paramsMap['settings'] = [fixed: true]
}

Integer getParamValueAdj(Map param) {
	Integer paramVal = getParamValue(param)

	if (param.hB) {	paramVal = addHighBytes(paramVal) }

	return paramVal
}

/*******************************************************************
 ***** Child/Other Functions
********************************************************************/
Integer addHighBytes(Integer val) {

	Integer newVal = Math.abs(val)
	//Bit 1 to indicate negatives
	if (val < 0) {
		newVal = negativeHB << 8 | newVal << 0
	}
	//logDebug "newVal ${hubitat.helper.HexUtils.integerToHexString(newVal,2)}"

	//Bit 2 for degrees in F
	if (tempUnits.toInteger()) {
		newVal = fahrenheitHB << 8 | newVal << 0
	}
	//logDebug "newVal ${hubitat.helper.HexUtils.integerToHexString(newVal,2)} | ${hubitat.helper.HexUtils.integerToHexString(val,1)}"

	return newVal
}

Integer fahrenheitToCelsiusIfNeeded(Integer val) {
	return ((tempUnits as Integer) ? val : fahrenheitToCelsius(val)).toInteger()
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
	return secureCmd(zwave.batteryV2.batteryGet())
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
