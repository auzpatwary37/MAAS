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
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.name.Names;

import MaaSPackages.MaaSPackages;
import MaaSPackages.MaaSPackagesReader;
import optimizerAgent.MaaSUtil;

public class MaaSDataLoader extends AbstractModule{
	
	private final MaaSPackages maasPacakges;
	private Map<String,Tuple<Double,Double>> timeBeans;

	public MaaSDataLoader() {
		this.maasPacakges = null;
	}

	public MaaSDataLoader(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario) {
		this.maasPacakges = packages;
		this.timeBeans = timeBeans;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.));
	}

	@Override
	public void install() {
		if (this.maasPacakges != null) {
			bind(MaaSPackages.class).annotatedWith(Names.named(MaaSUtil.MaaSPackagesAttributeName)).toInstance(this.maasPacakges);
		} else {
			bind(MaaSPackages.class).annotatedWith(Names.named(MaaSUtil.MaaSPackagesAttributeName)).toProvider(MaaSPackagesProvider.class).in(Singleton.class);
		}
		
		
		//bind the maas handler
		this.addEventHandlerBinding().to(MaaSDiscountAndChargeHandler.class).asEagerSingleton();
		bind(MaaSDiscountAndChargeHandler.class).in(Singleton.class);
		
		
	}
	
	private static class MaaSPackagesProvider implements Provider<MaaSPackages> {
		@Inject MaaSConfigGroup config;
		@Inject Config matsimConfig;
		@Inject Scenario scenario;
		@Override
		public MaaSPackages get() {
			String path = config.getPackagesFileURL(matsimConfig.getContext()).toString();
			MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(path);
			insertRandomMaaSPackage(scenario.getPopulation(),packages);
			return packages;
			
		}
		private static void insertRandomMaaSPackage(Population population, MaaSPackages packages) {
			Random rnd = MatsimRandom.getRandom();
			population.getPersons().values().forEach((p)->{
				if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
					p.getPlans().forEach((plan)->{
						int index = rnd.nextInt(packages.getMassPackages().size());
						plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, packages.getMassPackages().keySet().toArray()[index]);
					});
				}
			});
		}
	}
	
	

}
