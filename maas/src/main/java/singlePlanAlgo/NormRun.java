package singlePlanAlgo;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import optimizerAgent.MaaSOperatorOptimizationModule;

public class NormRun {
public static void main(String[] args) {
	
	
	//PropertyConfigurator.configure("src/main/resources/log4j.properties");
	
	
	final String writeFileLoc="toyScenarioLargeOct19/";
	//Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("data\\toyScenarioLargeData\\originalMeasurements_20_11.xml");
	//calibrationMeasurements.applyFator(.1);
//	Config initialConfig=ConfigUtils.createConfig();
//	ConfigUtils.loadConfig(initialConfig, "data/toyScenarioLargeData/configToyLargeMod.xml");
	
	Config initialConfig = RunTCS.setupConfig();
	initialConfig.plans().setInputPersonAttributeFile("new Data/data/personAttributesHKI.xml");
	
	initialConfig.global().setInsistingOnDeprecatedConfigVersion(false);
	
	ObjectAttributes att = new ObjectAttributes();
	new ObjectAttributesXmlReader(att).readFile("new Data/data/personAttributesHKI.xml");
	
	
	
	initialConfig.plans().setInputFile("new Data/data/populationHKI.xml");
	initialConfig.network().setInputFile("new Data/cal/output_network.xml.gz");
	initialConfig.vehicles().setVehiclesFile("new Data/data/VehiclesHKI.xml");
	
	initialConfig.network().setLaneDefinitionsFile("new Data/cal/output_lanes.xml");
	ConfigGroup sscg = initialConfig.getModule("signalsystems");
	sscg.addParam("signalcontrol", "new Data/cal/output_signal_control_v2.0.xml");
	sscg.addParam("signalgroups", "new Data/cal/output_signal_groups_v2.0.xml");
	sscg.addParam("signalsystems", "new Data/cal/output_signal_systems_v2.0.xml");
	initialConfig.transit().setTransitScheduleFile("new Data/cal/output_transitSchedule.xml.gz");
	initialConfig.transit().setVehiclesFile("new Data/cal/output_transitVehicles.xml.gz");
	//initialConfig.plans().setHandlingOfPlansWithoutRoutingMode(HandlingOfMainModeIdentifier);
	
	initialConfig.removeModule("roadpricing");
	initialConfig.qsim().setUsePersonIdForMissingVehicleId(true);
	
	
	//VehicleUtils.insertVehicleIdIntoAttributes(person, mode, vehicleId);
	initialConfig.strategy().setFractionOfIterationsToDisableInnovation(0.85);
//	initialConfig.qsim().setFlowCapFactor(0.14);
//	initialConfig.qsim().setStorageCapFactor(0.2);

	
//	LinkedHashMap<String,Double>initialParams=loadInitialParam(pReader,new double[] {-200,-240});
//	LinkedHashMap<String,Double>params=initialParams;
//	pReader.setInitialParam(initialParams);
//	Calibrator calibrator;

	
	//SimRun simRun=new SimRunImplToyLarge(100);
	Config config = initialConfig;
	config.controler().setLastIteration(6);
	config.controler().setOutputDirectory("toyScenarioLarge/output"+1222);
	config.transit().setUseTransit(true);
	//config.plansCalcRoute().setInsertingAccessEgressWalk(false);
	config.qsim().setUsePersonIdForMissingVehicleId(true);
	//config.controler().setLastIteration(50);
	config.parallelEventHandling().setNumberOfThreads(7);
	config.controler().setWritePlansInterval(2);
	config.qsim().setStartTime(0.0);
	config.qsim().setEndTime(28*3600);
	config.qsim().setStorageCapFactor(.14);
	config.controler().setWriteEventsInterval(20);
	config.planCalcScore().setWriteExperiencedPlans(false);
	//config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
	TransitRouterFareDynamicImpl.aStarSetting='c';
	TransitRouterFareDynamicImpl.distanceFactor=.01;
	
	//config.removeModule("signalsystems");
	
//	ConfigGeneratorLargeToy.reducePTCapacity(scenario.getTransitVehicles(),.15);
//	ConfigGeneratorLargeToy.reduceLinkCapacity(scenario.getNetwork(),.15);
	StrategySettings stratSets = new StrategySettings();
	
	stratSets.setStrategyName(MaaSPlanStrategy.class.getName());
	stratSets.setWeight(0.7);
	stratSets.setDisableAfter(200);
	stratSets.setSubpopulation("person_TCSwithCar");
	config.strategy().addStrategySettings(stratSets);
	config.addModule(new MaaSConfigGroup());
	
	config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages.xml");
	config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
	new ConfigWriter(config).write("test/config.xml");
	Scenario scenario = ScenarioUtils.loadScenario(config);
	for(LanesToLinkAssignment l2l:scenario.getLanes().getLanesToLinkAssignments().values()) {
		for(Lane l: l2l.getLanes().values()) {
			
			//Why this is done? 
			//Why .1 specifically??
			l.setCapacityVehiclesPerHour(1800*.15);
		}
	}
	
	
//	for(Person p:scenario.getPopulation().getPersons().values()) {
//		VehicleUtils.insertVehicleIdIntoAttributes(p, "car", Id.createVehicleId(p.getId().toString()));
//	}
	
	scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
	Controler controler = new Controler(scenario);
	controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
	controler.addOverridingModule(new MaaSOperatorOptimizationModule());
	ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
	SAXParser saxParser;
	
	try {
		saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse("new Data/data/busFare.xml", busFareGetter);
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
				"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
		
//		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
//				"fare/GMB.csv", "fare/light_rail_fares.csv"));
		
	} catch (ParserConfigurationException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	} catch (SAXException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	Signals.configure(controler);
	
	controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
	controler.run();

}
}
