package singlePlanAlgo;

import java.util.Map;
import java.util.Random;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.name.Names;

public class MAASDataLoader extends AbstractModule{
	
	private final MAASPackages maasPacakges;
	private Map<String,Tuple<Double,Double>> timeBeans;

	public MAASDataLoader() {
		this.maasPacakges = null;
	}

	public MAASDataLoader(MAASPackages packages, Map<String,Tuple<Double,Double>> timeBeans) {
		this.maasPacakges = packages;
		this.timeBeans = timeBeans;
	}

	@Override
	public void install() {
		if (this.maasPacakges != null) {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toInstance(this.maasPacakges);
		} else {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toProvider(MAASPackagesProvider.class).in(Singleton.class);
		}
		
		
		
	}
	
	private static class MAASPackagesProvider implements Provider<MAASPackages> {
		@Inject MAASConfigGroup config;
		@Inject Config matsimConfig;
		@Inject Scenario scenario;
		@Override
		public MAASPackages get() {
			String path = config.getPackagesFileURL(matsimConfig.getContext()).toString();
			MAASPackages packages = new MAASPackagesReader().readPackagesFile(path);
			insertRandomMAASPackage(scenario.getPopulation(),packages);
			return packages;
			
		}
		private static void insertRandomMAASPackage(Population population, MAASPackages packages) {
			Random rnd = MatsimRandom.getRandom();
			population.getPersons().values().forEach((p)->{
				p.getPlans().forEach((plan)->{
					int index = rnd.nextInt(packages.getMassPackages().size());
					plan.getAttributes().putAttribute(MaasStrategyModule.StrategyAttributeName, packages.getMassPackages().keySet().toArray()[index]);
				});
			});
		}
	}
	
	

}
