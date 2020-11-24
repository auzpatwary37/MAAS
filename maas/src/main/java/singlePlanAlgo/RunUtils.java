package singlePlanAlgo;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.scenario.ScenarioUtils;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;

public class RunUtils {

	public static Config provideConfig() {
		final String writeFileLoc="toyScenarioLargeOct19/";
		//Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("data\\toyScenarioLargeData\\originalMeasurements_20_11.xml");
		//calibrationMeasurements.applyFator(.1);
//		Config initialConfig=ConfigUtils.createConfig();
//		ConfigUtils.loadConfig(initialConfig, "data/toyScenarioLargeData/configToyLargeMod.xml");
		
		Config initialConfig = RunTCS.setupConfig();
		initialConfig.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		initialConfig.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
		initialConfig.plans().setInputFile("new Data/core/populationHKI.xml");
		initialConfig.network().setInputFile("new Data/cal/output_network.xml.gz");
		initialConfig.vehicles().setVehiclesFile("new Data/core/VehiclesHKI.xml");
		
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
		initialConfig.qsim().setFlowCapFactor(0.14);// this has to be applied physically for the internal model to work
		//initialConfig.qsim().setStorageCapFactor(0.2);

		
//		LinkedHashMap<String,Double>initialParams=loadInitialParam(pReader,new double[] {-200,-240});
//		LinkedHashMap<String,Double>params=initialParams;
//		pReader.setInitialParam(initialParams);
//		Calibrator calibrator;

		
		//SimRun simRun=new SimRunImplToyLarge(100);
		Config config = initialConfig;
		config.controler().setLastIteration(50);
		config.controler().setOutputDirectory("toyScenarioLarge/output"+1222);
		config.transit().setUseTransit(true);
		//config.plansCalcRoute().setInsertingAccessEgressWalk(false);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		//config.controler().setLastIteration(50);
		config.parallelEventHandling().setNumberOfThreads(7);
		config.controler().setWritePlansInterval(50);
		config.qsim().setStartTime(0.0);
		config.qsim().setEndTime(28*3600);
		config.qsim().setStorageCapFactor(.17);
		config.controler().setWriteEventsInterval(20);
		config.planCalcScore().setWriteExperiencedPlans(false);
		//config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		TransitRouterFareDynamicImpl.aStarSetting='c';
		TransitRouterFareDynamicImpl.distanceFactor=.01;
		
		
		 return config;
	}
	
}
