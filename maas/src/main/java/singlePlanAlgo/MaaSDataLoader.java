package singlePlanAlgo;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Random;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import MaaSPackages.MaaSPackagesReader;
import optimizerAgent.MaaSDiscountAndChargeHandlerPlatform;
import optimizerAgent.MaaSUtil;

public class MaaSDataLoader extends AbstractModule{
	
	private final MaaSPackages maasPacakges;
	private Map<String,Double> subsidyRatio = null;
	private final String type;

	public static final String typePlatform = "platform";
	public static final String typeOperator = "operator";
	public static final String typeOperatorPlatform = "operatorPlatform";
	
	public MaaSDataLoader(String type) {
		this.maasPacakges = null;
		this.type = type;
	}

	public MaaSDataLoader(String type, Map<String,Double>subsidyRatio) {
		this.maasPacakges = null;
		this.type = type;
		this.subsidyRatio = subsidyRatio;
	}
	public MaaSDataLoader(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario, String type) {
		this.maasPacakges = packages;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.));
		this.type = type;
	}

	public MaaSDataLoader(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario, String type,  Map<String,Double>subsidyRatio) {
		this.maasPacakges = packages;
		this.subsidyRatio = subsidyRatio;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.));
		this.type = type;
	}
	@Override
	public void install() {
		if (this.maasPacakges != null) {
			bind(MaaSPackages.class).annotatedWith(Names.named(MaaSUtil.MaaSPackagesAttributeName)).toInstance(this.maasPacakges);
		} else {
			bind(MaaSPackages.class).annotatedWith(Names.named(MaaSUtil.MaaSPackagesAttributeName)).toProvider(MaaSPackagesProvider.class).asEagerSingleton();
		}
		bind(String.class).annotatedWith(Names.named("MaaSType")).toInstance(this.type);
		MapBinder<String, Double> govSubsidyRatio = MapBinder.newMapBinder(
                binder(), String.class, Double.class, Names.named("SubsidyRatio"));
		if(this.subsidyRatio!=null){
			for(Entry<String, Double> e:this.subsidyRatio.entrySet())
				govSubsidyRatio.addBinding(e.getKey()).toInstance(e.getValue());
		}
		//bind the maas handler
		if(type.equals(MaaSDataLoader.typeOperator)) {
			this.addEventHandlerBinding().to(MaaSDiscountAndChargeHandler.class).asEagerSingleton();
		}else if(type.equals(MaaSDataLoader.typePlatform)){
			this.addEventHandlerBinding().to(MaaSDiscountAndChargeHandlerPlatform.class).asEagerSingleton();
		}else if(type.equals(MaaSDataLoader.typeOperatorPlatform)) {
			this.addEventHandlerBinding().to(MaaSDiscountAndHandlerOperatorPlatform.class).asEagerSingleton();
		}
		
		
			
	}
		
		
		
	
	
	private static class MaaSPackagesProvider implements Provider<MaaSPackages> {
		@Inject MaaSConfigGroup config;
		@Inject Config matsimConfig;
		@Inject Scenario scenario;
		@Override
		public MaaSPackages get() {
			String path = config.getPackagesFileURL(matsimConfig.getContext()).toString();
			MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(path);
			//insertRandomMaaSPackage(scenario.getPopulation(),packages);
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
