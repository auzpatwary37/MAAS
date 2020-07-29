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
		packages = new MaaSPackages(ts, true, 20, 3, FareCalculatorCreator.getHKFareCalculators(), 0, true);
		
		new MaaSPackagesWriter(packages).write("test/packages_July2020_20.xml");
	}

}
