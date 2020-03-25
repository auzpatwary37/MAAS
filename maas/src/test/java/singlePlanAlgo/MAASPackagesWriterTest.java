package singlePlanAlgo;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

public class MAASPackagesWriterTest {

	@Test
	public void test() {
		
		Config config = RunUtils.provideConfig();
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		TransitSchedule ts = scenario.getTransitSchedule();
		
		MAASPackages packages = null;
		packages = new MAASPackages(ts, true, 100, 3);
		
		new MAASPackagesWriter(packages).write("test/packaes.xml");
		
		MAASPackages packages1 = new MAASPackagesReader().readPackagesFile("test/packaes.xml");
		
		new MAASPackagesWriter(packages1).write("test/packages.xml");
		
	
		
	}

}
