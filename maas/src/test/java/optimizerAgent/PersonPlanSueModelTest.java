/**
 * 
 */
package optimizerAgent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
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
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;

/**
 * @author ashraf
 *
 */
class PersonPlanSueModelTest {

	
	
	public Injector createInjector() {
		
		Injector injector;
		
		Injector basicInjector = Guice.createInjector(new AbstractModule() {
			@Override
			protected void configure() {
				//bind the config
				Config config = ConfigUtils.createConfig();
				ConfigUtils.loadConfig(config, "toyScenario/toyScenarioData/config.xml");
				config.network().setInputFile("toyScenario/toyScenarioData/network.xml");
				config.vehicles().setVehiclesFile("toyScenario/toyScenarioDatavehicles.xml");
				config.transit().setTransitScheduleFile("toyScenario/toyScenarioData/transitSchedule.xml");
				config.transit().setVehiclesFile("toyScenario/toyScenarioData/transitVehicles.xml");
				config.plans().setInputFile("toyScenario/output_plans.xml.gz");
				Scenario scenario;
				
				
				bind(Config.class).toInstance(config);
				bind(Scenario.class).toInstance(scenario = ScenarioUtils.loadScenario(config));
				bind(TransitSchedule.class).toInstance(scenario.getTransitSchedule());
				bind(Vehicles.class).annotatedWith(Names.named("Vehicles")).toInstance(scenario.getVehicles());
				bind(Vehicles.class).annotatedWith(Names.named("TransitVehicles")).toInstance(scenario.getTransitVehicles());
				bind(Population.class).toInstance(scenario.getPopulation());
						
				MapBinder<String, FareCalculator> mapbinder = MapBinder.newMapBinder(binder(), String.class,
						FareCalculator.class);
				mapbinder.addBinding("train").to(MTRFareCalculator.class).in(Scopes.SINGLETON);
				bind(String.class).annotatedWith(Names.named("trainFareInput")).toInstance("toyScenario/toyScenarioData/Mtr_fare.csv");// add train fare file input
				try {
					mapbinder.addBinding("bus").toInstance(transitGenerator.createBusFareCalculator(scenario.getTransitSchedule()));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				Map<String,Tuple<Double,Double>> timeBean = new HashMap<>();
				for(int i = 0; i<24 ;i++) {
					timeBean.put(Integer.toString(i+1), new Tuple<>(i*3600.,(i+1)*3600.));
				}
				
				bind(timeBeanWrapper.class).toInstance(new timeBeanWrapper(timeBean));
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
		PersonPlanSueModel model = new PersonPlanSueModel(injector.getInstance(timeBeanWrapper.class).timeBean);
		
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link optimizerAgent.PersonPlanSueModel#performTransitVehicleOverlay(ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork, org.matsim.pt.transitSchedule.api.TransitSchedule, org.matsim.vehicles.Vehicles, java.lang.String)}.
	 */
	@Test
	void testPerformTransitVehicleOverlay() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link optimizerAgent.PersonPlanSueModel#populateModel(org.matsim.api.core.v01.Scenario, java.util.Map, singlePlanAlgo.MAASPackages)}.
	 */
	@Test
	void testPopulateModel() {
		fail("Not yet implemented");
	}

}

class timeBeanWrapper{
	final Map<String,Tuple<Double,Double>>timeBean;
	
	public timeBeanWrapper(final Map<String,Tuple<Double,Double>>timeBean) {
		this.timeBean = timeBean;
	}
}
