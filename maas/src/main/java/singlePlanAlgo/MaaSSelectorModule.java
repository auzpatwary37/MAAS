package singlePlanAlgo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import MaaSPackages.MaaSPackage;

public class MaaSSelectorModule implements   ActivityEndEventHandler,PlanStrategyModule{

	public static final String StrategyAttributeName = "MAAS_Plan";
	public LinkedHashMap<String,MaaSPackage> maasPackages= new LinkedHashMap<>();
	private Random rnd;
	
	

	@Override
	public void handleEvent(ActivityEndEvent event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// For preparing thread 
		
	}

	@Override
	public void handlePlan(Plan plan) {
		List<String> MAASKeys=new ArrayList<>(this.maasPackages.keySet());
		plan.getAttributes().putAttribute(StrategyAttributeName,rnd.nextInt(MAASKeys.size()));
		
	}

	@Override
	public void finishReplanning() {
		// For actual execution of replanning 
		
	}

}
