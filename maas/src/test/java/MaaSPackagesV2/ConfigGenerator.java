package MaaSPackagesV2;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;



/**
 * This class will generate the config file for simulation run
 * @author h
 *
 */
public class ConfigGenerator {
	
	public static Config generateToyConfig() {
		Config config=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "src/main/resources/toyScenarioData/config.xml");
		config.network().setInputFile("src/main/resources/toyScenarioData/network.xml");
		config.transit().setTransitScheduleFile("src/main/resources/toyScenarioData/transitSchedule.xml");
		config.vehicles().setVehiclesFile("src/main/resources/toyScenarioData/vehicles.xml");
		config.transit().setVehiclesFile("src/main/resources/toyScenarioData/transitVehicles.xml");
		config.transit().setUseTransit(true);
		//config.plansCalcRoute().setInsertingAccessEgressWalk(false);
		addPlanParameter(config.planCalcScore(), "home", 16*60*60);
		addPlanParameter(config.planCalcScore(), "work", 8*60*60);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(27*3600);
		config.global().setCoordinateSystem("arbitrary");
		config.parallelEventHandling().setNumberOfThreads(5);
		config.controler().setWritePlansInterval(50);
		config.global().setNumberOfThreads(4);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		config.controler().setWriteEventsInterval(50);
		config.strategy().addParam("ModuleProbability_1", "0.8");
		config.strategy().addParam("Module_1", "ChangeExpBeta");
		config.strategy().addParam("ModuleProbability_2", "0.05");
		config.strategy().addParam("Module_2", "ReRoute");
		config.strategy().addParam("ModuleProbability_3", "0.1");
		config.strategy().addParam("Module_3", "TimeAllocationMutator");
		config.strategy().addParam("ModuleProbability_4", "0.05");
		config.strategy().addParam("Module_4", "ChangeTripMode");
		
		return config;
	}
	
	public static void addPlanParameter(PlanCalcScoreConfigGroup config, String name, int typicalDuration){
		ActivityParams act = new ActivityParams(name);
		act.setTypicalDuration(typicalDuration);
		config.addActivityParams(act);
	}
}
