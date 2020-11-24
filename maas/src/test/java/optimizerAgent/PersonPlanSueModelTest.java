
package optimizerAgent;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;

import MaaSPackagesV2.FareCalculatorCreator;
import maasPackagesV2.MaaSPackages;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

/**
 * @author ashraf
 *
 */
class PersonPlanSueModelTest {

	
	public Injector createInjector() {
		
		Injector injector;
		
		Injector basicInjector = Guice.createInjector(new AbstractModule() {
			@SuppressWarnings("deprecation")
			@Override
			protected void configure() {
				//bind the config
				Config config = ConfigUtils.createConfig();
				ConfigUtils.loadConfig(config, "toyScenario/toyScenarioData/config.xml");
				config.network().setInputFile("toyScenario/toyScenarioData/network.xml");
				config.vehicles().setVehiclesFile("toyScenario/toyScenarioData/vehicles.xml");
				config.transit().setTransitScheduleFile("toyScenario/toyScenarioData/transitSchedule.xml");
				config.transit().setVehiclesFile("toyScenario/toyScenarioData/transitVehicles.xml");
				config.plans().setInputFile("toyScenario/output_plans.xml.gz");
				
				config.scenario().setSimulationPeriodInDays(1.0);
				Scenario scenario;
				scenario = ScenarioUtils.loadScenario(config);
				
				bind(Config.class).toInstance(config);
				bind(Scenario.class).toInstance(scenario);
				bind(Network.class).toInstance(scenario.getNetwork());
				bind(TransitSchedule.class).toInstance(scenario.getTransitSchedule());
				bind(Vehicles.class).annotatedWith(Names.named("Vehicles")).toInstance(scenario.getVehicles());
				bind(Vehicles.class).annotatedWith(Names.named("TransitVehicles")).toInstance(scenario.getTransitVehicles());
				bind(Population.class).toInstance(scenario.getPopulation());
				bind(double.class).annotatedWith(Names.named(DynamicRoutingModule.fareRateName)).toInstance(1.);
				bind(ParamReader.class).toInstance(new ParamReader("toyScenario/toyScenarioData/paramReaderToy.csv"));
				MaaSPackages packages = new MaaSPackages(scenario.getTransitSchedule(), true, 100, 0, FareCalculatorCreator.getToyScenarioFareCalculators(), 0, true);
				
				bind(MaaSPackages.class).toInstance(packages);
				
				MapBinder<String, FareCalculator> mapbinder = MapBinder.newMapBinder(binder(), String.class,
						FareCalculator.class);
				mapbinder.addBinding("train").to(MTRFareCalculator.class).in(Scopes.SINGLETON);
				bind(String.class).annotatedWith(Names.named("trainFareInput")).toInstance("toyScenario/toyScenarioData/Mtr_fare.csv");// add train fare file input
				try {
					mapbinder.addBinding("bus").toInstance(transitGenerator.createBusFareCalculator(scenario.getTransitSchedule(), Arrays.asList("toyScenario/toyScenarioData/Bus_1_fare_Test.csv","toyScenario/toyScenarioData/Bus_2_fare_Test.csv")));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				Map<String,Tuple<Double,Double>> timeBean = new HashMap<>();
				for(int i = 0; i<24 ;i++) {
					timeBean.put(Integer.toString(i+1), new Tuple<>(i*3600.,(i+1)*3600.));
				}
				
				bind(timeBeansWrapper.class).toInstance(new timeBeansWrapper(timeBean));
			}
			
		});
		
		return basicInjector;
	}
	

	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterAll
	static void tearDownAfterClass() throws Exception {
		
	}

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {
		
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link optimizerAgent.PersonPlanSueModel#PersonPlanSueModel(java.util.Map)}.
	 */
	@Test
	void testPersonPlanSueModel() {
		
		Injector injector = createInjector();
		PersonPlanSueModel model = new PersonPlanSueModel(injector.getInstance(timeBeansWrapper.class).timeBeans,injector.getInstance(Config.class));
		assertNotNull(model);
		
	}
	
	/**
	 * Test method for {@link optimizerAgent.PersonPlanSueModel#PerformAssignment()}.
	 */
	@Test
	void testPerformAssignement() {
		
		Injector injector = createInjector();
		PersonPlanSueModel model = new PersonPlanSueModel(injector.getInstance(timeBeansWrapper.class).timeBeans, injector.getInstance(Config.class));
		Scenario scenario = injector.getInstance(Scenario.class);
		model.populateModel(scenario, injector.getInstance(timeBeansWrapper.class).fareCalculators, injector.getInstance(MaaSPackages.class));
		ParamReader pReader = injector.getInstance(ParamReader.class);
		MaaSPackages packages = injector.getInstance(MaaSPackages.class);
		Random rnd = new Random();
		
		
		Population population = injector.getInstance(Population.class);
		population.getPersons().entrySet().parallelStream().forEach((p)->{
			p.getValue().getPlans().forEach((plan)->{
				plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, new SimpleTranslatedPlan(injector.getInstance(timeBeansWrapper.class).timeBeans, plan, scenario));
				plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, packages.getMassPackages().keySet().toArray()[rnd.nextInt(packages.getMassPackages().size())]);
			});
		});
		
		SUEModelOutput flow = model.performAssignment(population,pReader.ScaleUp(pReader.getDefaultParam()));
		Measurements m = model.performAssignment(population,pReader.ScaleUp(pReader.getDefaultParam()),null);
		Measurements mm = model.performAssignment(population,pReader.ScaleUp(pReader.getDefaultParam()),m);
		new MeasurementsWriter(m).write("test/testMeasurements_m.xml");
		new MeasurementsWriter(mm).write("test/testMeasurements_mm.xml");
		//assertNotNull(model);
		fail();
	}



}


