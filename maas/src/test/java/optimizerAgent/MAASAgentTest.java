package optimizerAgent;

import static org.junit.Assert.*;

import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;

import singlePlanAlgo.MAASPackages;
import singlePlanAlgo.MAASPackagesReader;
import singlePlanAlgo.RunUtils;

public class MAASAgentTest {

	@Test
	public void test() {
		// This will test the optimization agent insertion
		
		Config config = RunUtils.provideConfig();
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		MAASPackages packages = new MAASPackagesReader().readPackagesFile("test/packages.xml");
		
		OptimizerAgentCreator.createMAASAgents(packages, scenario.getPopulation(), "test/agentPop.xml");
		
		//fail("Not yet implemented");
	}

}
