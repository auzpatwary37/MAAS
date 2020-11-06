package testing;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;



public class ToyScenarioTestCase {
	public static void main(String[] args) {
		Config config=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "src/main/resources/toyScenarioData/config.xml");
		config.network().setInputFile("src/main/resources/toyScenarioData/network.xml");
		config.transit().setTransitScheduleFile("src/main/resources/toyScenarioData/transitSchedule.xml");
		config.vehicles().setVehiclesFile("src/main/resources/toyScenarioData/vehicles.xml");
		config.transit().setVehiclesFile("src/main/resources/toyScenarioData/transitVehicles.xml");
		config.transit().setUseTransit(true);
		//config.plansCalcRoute().setInsertingAccessEgressWalk(false);
		//PopulationGenerator.addPlanParameter(config.planCalcScore(), "home", 16*60*60);
		//PopulationGenerator.addPlanParameter(config.planCalcScore(), "work", 8*60*60);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
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
	}

}
