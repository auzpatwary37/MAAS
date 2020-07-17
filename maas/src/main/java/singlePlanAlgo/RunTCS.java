package singlePlanAlgo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;

import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import running.RunUtils;

public class RunTCS {
	private static void modifyPopulation(Population population){
		for(Person person: population.getPersons().values()) {
			Plan plan = person.getPlans().get(0);
			Plan newPlan = PopulationUtils.createPlan(person);
			List<PlanElement> toAdd = new ArrayList<PlanElement>();
			boolean swifted = false;
			for(PlanElement pe: plan.getPlanElements()) {
				if(pe instanceof Leg) {
					Leg leg = (Leg) pe;
					double depTime = leg.getDepartureTime().seconds();
					if(swifted|| (depTime <= 2 * 3600 && depTime >= 0)) {
						leg.setDepartureTime(depTime + 24 * 3600);
						//swifted = true;
						toAdd.add(leg);
					}else {
						newPlan.addLeg(leg);
					}
					
				}else if (pe instanceof Activity) {
					Activity act = (Activity) pe;
					double startTime = act.getStartTime().seconds();
					double endTime = act.getEndTime().seconds();
					if(swifted || (endTime <= 2 * 3600 && endTime >= 0)) {
						act.setEndTime(endTime + 24 * 3600);
						act.setStartTime(startTime + 24 * 3600);
						toAdd.add(act);
					}else {
						newPlan.addActivity(act);
					}
					
				}else {
					throw new RuntimeException("The PlanElement is strange!");
				}
			}
			
			for(PlanElement pe: toAdd) {
				if(pe instanceof Leg) {
					newPlan.addLeg((Leg) pe);
				}else if (pe instanceof Activity) {
					newPlan.addActivity((Activity) pe);
				}
			}
			person.removePlan(plan);
			person.addPlan(newPlan);
		}
	}
	
	public static Config setupConfig() {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "new Data/data/config_clean.xml");
		
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		
		Config configGV = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configGV, "new Data/data/config_Ashraf.xml");
		for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
			if(act.getActivityType().contains("Usual place of work")) {
				act.setMinimalDuration(3600);
			}
			if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
				config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(act);
			}
		}
		
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(300);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.95);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		config.controler().setWriteEventsInterval(25);
		config.controler().setWritePlansInterval(25);
		config.planCalcScore().setWriteExperiencedPlans(true);
		config.controler().setOutputDirectory("outputFullHKOct2018_0.1/");
		
		config.plans().setInputFile("new Data/data/populationHKI.xml");
		config.plans().setInputPersonAttributeFile("new Data/data/personAttributesHKI.xml");
		//config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME"); /* This is the default anyway. */
		
		config.vehicles().setVehiclesFile("new Data/data/VehiclesHKI.xml");
		config.qsim().setNumberOfThreads(10);
//		config.qsim().setStorageCapFactor(2);
//		config.qsim().setFlowCapFactor(1.2);
		config.global().setNumberOfThreads(24);
		config.parallelEventHandling().setNumberOfThreads(14);
		config.parallelEventHandling().setEstimatedNumberOfEvents((long) 1000000000);
		

		
//        EmissionsConfigGroup ecg = new EmissionsConfigGroup() ;
//        config.addModule(ecg);
		config.removeModule("emissions");
		return config;
	}
	
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException, InterruptedException {


		TransitRouterFareDynamicImpl.distanceFactor = 0.034;
		
		//config.plans().setInputFile("outputFullHKAddedBus/ITERS/it.150/150.plans.xml.gz");
		Config config = setupConfig();

		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.01);
		RunUtils.scaleDownPt(scenario.getTransitVehicles(), 0.01);
		
//		FileWriter writer = new FileWriter("vehicleType.csv");
//		StringBuilder sb = new StringBuilder();
//		sb.append("Name,PCU\n");		
//		for(VehicleType vt: scenario.getVehicles().getVehicleTypes().values()) {
//			sb.append(vt.getDescription()+","+vt.getPcuEquivalents()+"\n");
//		}
//		for(VehicleType vt: scenario.getTransitVehicles().getVehicleTypes().values()) {
//			sb.append(vt.getId().toString()+","+vt.getPcuEquivalents()+"\n");
//		}
//		writer.write(sb.toString());
//		writer.close();
		
//		RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.1);
		Controler controler = new Controler(scenario);

		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);

//		busFareGetter.get().checkValidity();
		// Add the signal module to the controller
		Signals.configure(controler);
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
				"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
//		controler.addOverridingModule(new AbstractModule() {
//			@Override
//			public void install() {
//				bind(PRAISEEmissionModule.class).asEagerSingleton();
//			}
//		});
		
		
		controler.run();
	}
}

