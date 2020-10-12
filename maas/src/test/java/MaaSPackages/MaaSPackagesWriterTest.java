package MaaSPackages;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import singlePlanAlgo.RunUtils;
import transitCalculatorsWithFare.FareLink;

class MaaSPackagesWriterTest {

	@Test
	void test() {
		Config config = RunUtils.provideConfig();
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		TransitSchedule ts = scenario.getTransitSchedule();
		
		MaaSPackages packages = null;
		packages = new MaaSPackages(ts, true, 20, 3, FareCalculatorCreator.getHKFareCalculators(), 0, true);
		
		new MaaSPackagesWriter(packages).write("test/packages_July2020_400.xml");

		MaaSPackages allPack = new MaaSPackages();
		String operatorId = "allPack";
		String Id = "platform";
		int maxTaxiTrip = 0;
		double cost = 20;
		double packageExpDate = 3;
		Map<String,FareLink> fareLinks = new HashMap<>();
		Map<String,Double>discounts = new HashMap<>();
		Map<String,Double>fullFare = new HashMap<>();
		for(Entry<String, MaaSPackage> m : packages.getMassPackages().entrySet()) {
			fareLinks.putAll(m.getValue().getFareLinks());
			discounts.putAll(m.getValue().getDiscounts());
			fullFare.putAll(m.getValue().getFullFare());
		}
		MaaSPackage pac = new MaaSPackage(Id, operatorId, fareLinks, discounts, fullFare, maxTaxiTrip, cost, packageExpDate);
		allPack.addMaaSPacakge(pac);
		new MaaSPackagesWriter(allPack).write("test/packages_all.xml");
	}

}
