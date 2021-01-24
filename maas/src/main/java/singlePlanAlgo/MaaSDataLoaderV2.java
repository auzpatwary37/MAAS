package singlePlanAlgo;

import java.util.HashMap;
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

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;

import optimizerAgent.MaaSUtil;
import optimizerAgent.PlanTranslationControlerListener;

public class MaaSDataLoaderV2 extends AbstractModule{
	
	private final MaaSPackages maasPacakges;
	private Map<String,Double> subsidyRatio = null;
	private final String type;

	public static final String typeGovt = "Govt";
	public static final String typeOperator = "operator";
	public static final String typeGovtTT = "GovtTT";
	
	public MaaSDataLoaderV2(String type) {
		this.maasPacakges = null;
		this.type = type;
	}

	public MaaSDataLoaderV2(String type, Map<String,Double>subsidyRatio) {
		this.maasPacakges = null;
		this.type = type;
		this.subsidyRatio = subsidyRatio;
	}
	public MaaSDataLoaderV2(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario, String type) {
		this.maasPacakges = packages;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.),null,null,true);
		this.type = type;
	}
	public MaaSDataLoaderV2(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario, String type,Map<String,Map<String,Double>> variables, Map<String,Map<String,Tuple<Double,Double>>>variableLimits) {
		this.maasPacakges = packages;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.),variables,variableLimits,true);
		this.type = type;
	}

	public MaaSDataLoaderV2(MaaSPackages packages, Map<String,Tuple<Double,Double>> timeBeans,Scenario scenario, String type,  Map<String,Double>subsidyRatio) {
		this.maasPacakges = packages;
		this.subsidyRatio = subsidyRatio;
		MaaSUtil.createMaaSOperator(this.maasPacakges, scenario.getPopulation(), null, new Tuple<>(.5,2.),null,null,true);
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
		
		//bind the maas handler
		//this.addEventHandlerBinding().to(MaaSDiscountAndChargeHandlerV2.class).asEagerSingleton();
		bind(MaaSDiscountAndChargeHandlerV2.class).asEagerSingleton();
		this.addControlerListenerBinding().to(PlanTranslationControlerListener.class);
		
		
			
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
