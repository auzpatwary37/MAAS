package MaaSPackages;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import singlePlanAlgo.RunUtils;

class MaaSPackagesWriterTest {

	@Test
	void test() {
		Config config = RunUtils.provideConfig();
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		TransitSchedule ts = scenario.getTransitSchedule();
		
		MaaSPackages packages = null;
		packages = new MaaSPackages(ts, true, 100, 3, FareCalculatorCreator.getHKFareCalculators(), 0, true);
		
		new MaaSPackagesWriter(packages).write("test/packaes_May2020.xml");
		
		MaaSPackages packages1 = new MaaSPackagesReader().readPackagesFile("test/packaes_May2020.xml");
		
		new MaaSPackagesWriter(packages1).write("test/packages_May2020.xml");
		fail("Not yet implemented");
	}

}
