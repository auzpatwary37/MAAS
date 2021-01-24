package singlePlanAlgo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;

import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import dynamicTransitRouter.costs.PTRecordHandler;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.ODFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import matsimIntegrate.DynamicRoutingModuleWithMaas;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSOperatorOptimizationModule;
import optimizerAgent.MaaSOperatorStrategy;
import optimizerAgent.MaaSUtil;
import running.RunUtils;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;

/**
 * A small example to ensure that the MaaS model works
 * 
 * @author Enoch
 * Created 31 July 2020
 */
public class SmallExample {
	Map<String, FareCalculator> fareCalMap = new HashMap<>();
	
	private void addVehicleAndDeparture(TransitScheduleFactory factory, Vehicles transitVehicles, TransitRoute tr, String prefix) {
		// Create and add vehicles
		VehiclesFactory vf = transitVehicles.getFactory();
		VehicleType vt = vf.createVehicleType(Id.create(prefix, VehicleType.class));

		VehicleCapacity vc = vt.getCapacity();
		vc.setSeats(100);
		vc.setStandingRoom(30);
		transitVehicles.addVehicleType(vt);

		for (int i = 0; i < 3; i++) {
			Vehicle v = vf.createVehicle(Id.createVehicleId(prefix + i), vt);
			transitVehicles.addVehicle(v);

			// Create and add departures
			Departure d = factory.createDeparture(Id.create(i + "", Departure.class), i * 100.0);
			d.setVehicleId(v.getId());
			tr.addDeparture(d);
		}
	}
	
	private ZonalFareCalculator createBusLine(Network net, TransitSchedule ts, Vehicles transitVehicles, double sectionFare) {
		//Create bus line
		Node bus1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("bus_1"), new Coord(10000, 0));
		Node bus2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("bus_2"), new Coord(5000, 0));
		Node bus3 = NetworkUtils.createAndAddNode(net, Id.createNodeId("bus_3"), new Coord(0,0));

		NetworkUtils.createAndAddLink(net, Id.createLinkId("bl1"), bus1, bus2, 5000, 50, 2000, 1);
		NetworkUtils.createAndAddLink(net, Id.createLinkId("bl2"), bus2, bus3, 5000, 50, 2000, 1);

		TransitScheduleFactory factory = ts.getFactory();

		// Create and add stop facilities and route stop.
		TransitStopFacility work_bus = factory.createTransitStopFacility(Id.create("work_bus", TransitStopFacility.class),
				new Coord(0, 0), false);
		work_bus.setLinkId(Id.createLinkId("bl2"));
		ts.addStopFacility(work_bus);
		TransitRouteStop routeStopC = factory.createTransitRouteStop(work_bus, 200, 220);

		TransitStopFacility busStopA = factory.createTransitStopFacility(Id.create("busStopA", TransitStopFacility.class),
				new Coord(10000, 0), false);
		busStopA.setLinkId(Id.createLinkId("bl1"));
		ts.addStopFacility(busStopA);
		TransitRouteStop routeStopA = factory.createTransitRouteStop(busStopA, 0, 20);

		TransitStopFacility busStopB = factory.createTransitStopFacility(Id.create("busStopB", TransitStopFacility.class),
				new Coord(5000, 0), false);
		busStopB.setLinkId(Id.createLinkId("bl2"));
		ts.addStopFacility(busStopB);
		TransitRouteStop routeStopB = factory.createTransitRouteStop(busStopB, 100, 120);
		
		ZonalFareCalculator fareCal = new ZonalFareCalculator(ts);
		// Create and add transitline
		TransitLine tl = factory.createTransitLine(Id.create("bus", TransitLine.class));
		ts.addTransitLine(tl);

		// Create and add transitRoute
		NetworkRoute nr = RouteUtils.createNetworkRoute(Lists.newArrayList(Id.createLinkId("bl1"), Id.createLinkId("bl2")), net);
		TransitRoute tr = factory.createTransitRoute(Id.create("bus", TransitRoute.class), nr,
				Lists.newArrayList(routeStopA, routeStopB, routeStopC), "bus");
		tl.addRoute(tr);
		addVehicleAndDeparture(factory, transitVehicles, tr, "bus");
		
		fareCal.setFullFare(tl.getId(), 22);
		fareCal.addRoute(tl.getId(), tr.getId(), 22);
		fareCal.addSectionFare(tl.getId(), tr.getId(), 1, sectionFare);
		
		return fareCal;
	}
	
	private FareCalculator createTramLine(Network net, TransitSchedule ts, Vehicles transitVehicles, double sectionFare) {
		TransitScheduleFactory factory = ts.getFactory();

		// Create and add stop facilities and route stop.
		TransitStopFacility work_bus = ts.getFacilities().get(Id.create("work_bus", TransitStopFacility.class));
		TransitRouteStop routeStopC = factory.createTransitRouteStop(work_bus, 200, 220);

		TransitStopFacility busStopA = ts.getFacilities().get(Id.create("busStopA", TransitStopFacility.class));
		TransitRouteStop routeStopA = factory.createTransitRouteStop(busStopA, 0, 20);

		TransitStopFacility busStopB = ts.getFacilities().get(Id.create("busStopB", TransitStopFacility.class));
		TransitRouteStop routeStopB = factory.createTransitRouteStop(busStopB, 100, 120);
		
		// Create and add transitline
		TransitLine tl = factory.createTransitLine(Id.create("tram", TransitLine.class));
		ts.addTransitLine(tl);

		// Create and add transitRoute
		NetworkRoute nr = RouteUtils.createNetworkRoute(Lists.newArrayList(Id.createLinkId("bl1"), Id.createLinkId("bl2")), net);
		TransitRoute tr = factory.createTransitRoute(Id.create("bus", TransitRoute.class), nr,
				Lists.newArrayList(routeStopA, routeStopB, routeStopC), "tram");
		tl.addRoute(tr);
		addVehicleAndDeparture(factory, transitVehicles, tr, "tram");
		
		return new UniformFareCalculator(sectionFare);
	}
	
	private FareCalculator createTrainLine(Network net, TransitSchedule ts, Vehicles transitVehicles, double sectionFare) {
		Node train1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("train_1"), new Coord(0, 10000));
		Node train2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("train_2"), new Coord(0, 5000));
		Node train3 = NetworkUtils.createAndAddNode(net, Id.createNodeId("train_3"), new Coord(0,0));

		NetworkUtils.createAndAddLink(net, Id.createLinkId("tl1"), train1, train2, 5000, 50, 2000, 1);
		NetworkUtils.createAndAddLink(net, Id.createLinkId("tl2"), train2, train3, 5000, 50, 2000, 1);	
		
		TransitScheduleFactory factory = ts.getFactory();

		// Create and add stop facilities and route stop.
		TransitStopFacility work_bus = factory.createTransitStopFacility(Id.create("work_train", TransitStopFacility.class),
				new Coord(0, 0), false);
		work_bus.setLinkId(Id.createLinkId("tl2"));
		ts.addStopFacility(work_bus);
		TransitRouteStop routeStopC = factory.createTransitRouteStop(work_bus, 200, 220);

		TransitStopFacility trainStopA = factory.createTransitStopFacility(Id.create("trainStopA", TransitStopFacility.class),
				new Coord(0, 10000), false);
		trainStopA.setLinkId(Id.createLinkId("tl1"));
		ts.addStopFacility(trainStopA);
		TransitRouteStop routeStopA = factory.createTransitRouteStop(trainStopA, 0, 20);

		TransitStopFacility trainStopB = factory.createTransitStopFacility(Id.create("trainStopB", TransitStopFacility.class),
				new Coord(0, 5000), false);
		trainStopB.setLinkId(Id.createLinkId("tl2"));
		ts.addStopFacility(trainStopB);
		TransitRouteStop routeStopB = factory.createTransitRouteStop(trainStopB, 100, 120);
		
		// Create and add transitline
		TransitLine tl = factory.createTransitLine(Id.create("train", TransitLine.class));
		ts.addTransitLine(tl);

		// Create and add transitRoute
		NetworkRoute nr = RouteUtils.createNetworkRoute(Lists.newArrayList(Id.createLinkId("tl1"), Id.createLinkId("tl2")), net);
		TransitRoute tr = factory.createTransitRoute(Id.create("train", TransitRoute.class), nr,
				Lists.newArrayList(routeStopA, routeStopB, routeStopC), "train");
		tl.addRoute(tr);
		
		addVehicleAndDeparture(factory, transitVehicles, tr, "train");
		
		ODFareCalculator fareCal = new ODFareCalculator();
		fareCal.addODFare(trainStopA.getId(), work_bus.getId(), 22);
		fareCal.addODFare(trainStopA.getId(), trainStopB.getId(), sectionFare);
		fareCal.addODFare(trainStopB.getId(), work_bus.getId(), sectionFare);
		fareCal.addODFare(trainStopA.getId(), trainStopA.getId(), sectionFare);
		fareCal.addODFare(trainStopB.getId(), trainStopB.getId(), sectionFare);
		fareCal.addODFare(work_bus.getId(), work_bus.getId(), sectionFare);
		return fareCal;
	}
	
	private void createPeople(Population population, Coord homeCoord, int num) {
		PopulationFactory fac = population.getFactory();
		for (int i = 0; i < num; i++) { // 100 people trying to take the bus
			Person person = fac.createPerson(Id.createPersonId(homeCoord.toString()+i));
			PopulationUtils.putSubpopulation(person, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION);
			Plan plan = PopulationUtils.createPlan(person);
			person.addPlan(plan);

			Activity homeAct = PopulationUtils.createActivityFromCoord("home", homeCoord);
			homeAct.setEndTime(0);
			plan.addActivity(homeAct);

			plan.addLeg(PopulationUtils.createLeg("pt"));

			Activity workAct = PopulationUtils.createActivityFromCoord("work", new Coord(0, 0));
			workAct.setEndTime(2000);
			plan.addActivity(workAct);

			population.addPerson(person);
		}
	}
	
	/**
	 * A simple function to create a small scenario for demonstration.
	 * @return
	 */
	private Scenario createSimpleScenario(double sectionFare, int populationCount) {
		Scenario scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
	
		Network net = scenario.getNetwork();

		//Create bus and train line
		FareCalculator busFareCal = createBusLine(net, scenario.getTransitSchedule(), scenario.getTransitVehicles(), sectionFare);
		FareCalculator trainFareCal = createTrainLine(net, scenario.getTransitSchedule(), scenario.getTransitVehicles(), sectionFare);
		fareCalMap.put("bus", busFareCal);
		fareCalMap.put("train", trainFareCal);
		
		Config config = scenario.getConfig();
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(5000);
		config.travelTimeCalculator().setTraveltimeBinSize(500);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.95);
		config.controler().setOutputDirectory("./output/small"+sectionFare);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setLastIteration(150);
		config.planCalcScore().setWriteExperiencedPlans(true);
		config.transit().setUseTransit(true);

		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(16 * 60 * 60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8 * 60 * 60);
		config.planCalcScore().addActivityParams(work);
		
		//Initialize the score parameters
		ScoringParameterSet ss = config.planCalcScore().getOrCreateScoringParameters(PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION);
		ss.getOrCreateModeParams("car").setMarginalUtilityOfTraveling(-20000);
		ss.getOrCreateModeParams("car").setMarginalUtilityOfDistance(-1000);
		ss.getOrCreateModeParams("car").setMonetaryDistanceRate(0);
		ss.getOrCreateModeParams("pt").setMarginalUtilityOfTraveling(-60);
		ss.getOrCreateModeParams("pt").setMarginalUtilityOfDistance(0);
		ss.getOrCreateModeParams("pt").setMonetaryDistanceRate(-0.0001);
		ss.setMarginalUtilityOfMoney(1);
		ss.getOrCreateModeParams("pt").setMonetaryDistanceRate(0);
		ss.getOrCreateModeParams("walk").setMarginalUtilityOfTraveling(-50);
		ss.getOrCreateModeParams("walk").setMonetaryDistanceRate(-0.1);
		ss.setPerforming_utils_hr(100);
		ss.setMarginalUtlOfWaitingPt_utils_hr(-6.);
		ss.addActivityParams(home);
		ss.addActivityParams(work);
		
		RunUtils.createStrategies(config, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, 0.015, 0.01, 0, 0);
		
		//Create several persons who always take pt, at 4 points
		Population popu = scenario.getPopulation();
		if(populationCount > 0){
			createPeople(popu, new Coord(10000, 0), populationCount);
			createPeople(popu, new Coord(5000, 0), populationCount);
			createPeople(popu, new Coord(0, 5000), populationCount);
			createPeople(popu, new Coord(0, 10000), populationCount);
		}
		
		return scenario;
	}
	
	static void additionalSettingsForMaaS(Scenario scenario, String firstSuPopName, String inputPackage) {
		
		Config config = scenario.getConfig();
		config.addModule(new MaaSConfigGroup());
		config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE, inputPackage);
		
		MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(inputPackage); //It has to be consistent with the config.

		//Create activity for MaaS operator
		Activity act = MaaSUtil.createMaaSOperator(packages, scenario.getPopulation(), "test/agentPop.xml",new Tuple<>(.5,2.5),null,null,true);
		ActivityParams param = new ActivityParams(act.getType());
		param.setTypicalDuration(20*3600);
		param.setMinimalDuration(8*3600);
		param.setScoringThisActivityAtAll(false);	
		
		//Copy the set of the activity params to the MaaS operators
		ScoringParameterSet s = config.planCalcScore().getOrCreateScoringParameters(MaaSOperator.type);
		s.addActivityParams(param);
		ScoringParameterSet ss = config.planCalcScore().getScoringParameters(firstSuPopName);

		s.getOrCreateModeParams("car").setMarginalUtilityOfTraveling(ss.getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
		s.getOrCreateModeParams("car").setMarginalUtilityOfDistance(ss.getOrCreateModeParams("car").getMarginalUtilityOfDistance());
		s.setMarginalUtilityOfMoney(ss.getMarginalUtilityOfMoney());
		s.getOrCreateModeParams("car").setMonetaryDistanceRate(ss.getOrCreateModeParams("car").getMonetaryDistanceRate());
		s.getOrCreateModeParams("walk").setMarginalUtilityOfTraveling(ss.getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
		s.getOrCreateModeParams("walk").setMonetaryDistanceRate(ss.getOrCreateModeParams("walk").getMarginalUtilityOfDistance());
		s.setPerforming_utils_hr(ss.getPerforming_utils_hr());
		s.setMarginalUtlOfWaitingPt_utils_hr(ss.getMarginalUtlOfWaitingPt_utils_hr());
		
		config.strategy().addStrategySettings(MaaSEffectTest.createStrategySettings(
							MaaSPlanStrategy.class.getName(),.2,400,PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION));
		RunUtils.addStrategy(config, "KeepLastSelected", MaaSUtil.MaaSOperatorAgentSubPopulationName, 1, 400);
	}
	
	//@Test
	/**
	 * It is a test for running the controler to see if the agents would converge to the desired result
	 */
	void testHalfUsePackage() {
		Scenario scenario = createSimpleScenario(19, 20);
		additionalSettingsForMaaS(scenario, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, "src/test/resources/packages/packages_simple19.0.xml");
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		controler.addOverridingModule(new DynamicRoutingModuleWithMaas(fareCalMap));
		controler.run();
	}
	
	//@Test
	/**
	 * It is a test for running the controler to see if the agents would converge to the desired result
	 */
	void testStickPackage() {
		Scenario scenario = createSimpleScenario(21, 20);
		additionalSettingsForMaaS(scenario, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, "src/test/resources/packages/packages_simple21.0.xml");
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		controler.addOverridingModule(new DynamicRoutingModuleWithMaas(fareCalMap));
		controler.run();
	}
	
//	@Test
	/**
	 * It is a test for optimization of the package price.
	 */
	void testPackageOptimization() {
		Scenario scenario = createSimpleScenario(19, 20);
		additionalSettingsForMaaS(scenario, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, "src/test/resources/packages/packages_simple19.0.xml");
		scenario.getConfig().strategy().addStrategySettings(MaaSEffectTest.createStrategySettings(
				MaaSOperatorStrategy.class.getName(), 1, 200, MaaSUtil.MaaSOperatorAgentSubPopulationName));
		scenario.getConfig().controler().setOutputDirectory("./output/smallTestOptimizePackage");
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		controler.addOverridingModule(new DynamicRoutingModuleWithMaas(fareCalMap));
		controler.run();
	}
	
	@Test
	/**
	 * It is a test for optimization of the package price.
	 * It is suppose that the bus package fare would be close to 15, which is the fare of tram, to get more passengers.
	 */
	void testPackageOptimizationWithTram() {
		Scenario scenario = createSimpleScenario(19, 20);
		additionalSettingsForMaaS(scenario, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, "src/test/resources/packages/packages_simple19.0.xml");
		scenario.getConfig().strategy().addStrategySettings(MaaSEffectTest.createStrategySettings(
				MaaSOperatorStrategy.class.getName(), 100, 350, MaaSUtil.MaaSOperatorAgentSubPopulationName));
		scenario.getConfig().controler().setOutputDirectory("./output/smallTestOptimizeWithTram");
		scenario.getConfig().controler().setLastIteration(400);
		FareCalculator tramFareCal = createTramLine(scenario.getNetwork(), scenario.getTransitSchedule(), scenario.getTransitVehicles(), 15);
		fareCalMap.put("tram", tramFareCal);
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		controler.addOverridingModule(new DynamicRoutingModuleWithMaas(fareCalMap));
		controler.run();
	}
	
	private static boolean checkIfContainTram(Person person) {
		Plan plan = person.getSelectedPlan();
		for(PlanElement pe: plan.getPlanElements()) {
			if(pe instanceof Leg) {
				if (((Leg) pe).getRoute().getRouteDescription()!=null && ((Leg) pe).getRoute().getRouteDescription().contains("tram")) {
					return true;
				}
			}
		}
		return false;
	}
	
	//@Test
	/**
	 * It is a test for the shortest path algorithm, what is the shortest path.
	 */
	void testShortestPath() {
		Scenario scenario = createSimpleScenario(19, 2);
		additionalSettingsForMaaS(scenario, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION, "src/test/resources/packages/packages_simple19.0.xml");
		scenario.getConfig().strategy().addStrategySettings(MaaSEffectTest.createStrategySettings(
				MaaSOperatorStrategy.class.getName(), 1, 200, MaaSUtil.MaaSOperatorAgentSubPopulationName));
		scenario.getConfig().controler().setOutputDirectory("./output/testShortestPath");
		scenario.getConfig().controler().setLastIteration(0);
		FareCalculator tramFareCal = createTramLine(scenario.getNetwork(), scenario.getTransitSchedule(), scenario.getTransitVehicles(), 15);
		fareCalMap.put("tram", tramFareCal);
		
		scenario.getPopulation().getPersons().get(Id.create("[x=10000.0 | y=0.0]0", Person.class)).getSelectedPlan().
				getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, "bus");
		
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		controler.addOverridingModule(new DynamicRoutingModuleWithMaas(fareCalMap));
		controler.run();
		
		assertFalse(checkIfContainTram(scenario.getPopulation().getPersons().get(Id.create("[x=10000.0 | y=0.0]0", Person.class))));
		assertTrue(checkIfContainTram(scenario.getPopulation().getPersons().get(Id.create("[x=10000.0 | y=0.0]1", Person.class))));
	}
	
	//@Test
	/**
	 * Test whether the initialization is going to suits some parameters, such as no subscription to plans.
	 */
	void testBasic() {
		
	}
	
}
