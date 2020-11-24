package MaaSPackagesV2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import singlePlanAlgo.RunUtils;
import transitCalculatorsWithFare.FareLink;

import maasPackagesV2.*;

class MaaSPackagesWriterTest {

	@Test
	void test() {
		Config config = RunUtils.provideConfig();
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		TransitSchedule ts = scenario.getTransitSchedule();
		
		MaaSPackages packages = null;
		packages = new MaaSPackages(ts, true, 20, 3, FareCalculatorCreator.getHKFareCalculators(), 0.5, false);
		packages.updateOperatorToFareLinkMap();
		new MaaSPackagesWriter(packages).write("test/packages_July2020_20.xml");
		MaaSPackages readPackages = new MaaSPackagesReader().readPackagesFile("test/packages_July2020_20.xml");
		MaaSPackages allPack = new MaaSPackages();
		String operatorId = "platform";
		String Id = "allPack";
		int maxTaxiTrip = 0;
		double cost = 20;
		double packageExpDate = 3;
		Map<String,FareLink> fareLinks = new HashMap<>();
		Map<String,Double>discounts = new HashMap<>();
		Map<String,Double>fullFare = new HashMap<>();
		Map<String,Set<String>> operatorSpecificFareLinks = new HashMap<>();
		for(Entry<String, MaaSPackage> m : packages.getMassPackages().entrySet()) {
			fareLinks.putAll(m.getValue().getFareLinks());
			discounts.putAll(m.getValue().getDiscounts());
			fullFare.putAll(m.getValue().getFullFare());
			for(Entry<String,Set<String>>e:m.getValue().getOperatorSpecificFareLinks().entrySet()){
				if(!operatorSpecificFareLinks.containsKey(e.getKey())) {
					operatorSpecificFareLinks.put(e.getKey(), new HashSet<>());
				}
				operatorSpecificFareLinks.get(e.getKey()).addAll(e.getValue());
			}
		}
		MaaSPackage pac = new MaaSPackage(Id, operatorId, fareLinks, discounts, fullFare,operatorSpecificFareLinks, maxTaxiTrip, cost, packageExpDate);
		allPack.addMaaSPacakge(pac);
		allPack.updateOperatorToFareLinkMap();
		
		allPack.getMassPackages().get("allPack").getFareLinks().entrySet().forEach(fl->{
			if(allPack.getOperatorId(fl.getValue())==null) {
				System.out.println("Debug");
			};
		});
		
		new MaaSPackagesWriter(allPack).write("test/packages_all.xml");
		MaaSPackages p1 = new MaaSPackagesReader().readPackagesFile("test/packages_all.xml");
		p1.getMassPackages().get("allPack").getFareLinks().entrySet().forEach(fl->{
			String s = fl.getValue().toString();
			if(allPack.getOperatorId(fl.getValue())==null) {
				System.out.println("Debug");
				System.out.println(s);
				String ss = allPack.getMassPackages().get("allPack").getFareLinks().get(fl.getValue().toString()).toString();
				System.out.println(ss);
				System.out.println(s.equals(ss));
				System.out.println(ss.equals(s));
			};
		});
	}

}
