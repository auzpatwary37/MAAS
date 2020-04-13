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

import com.google.inject.Inject;

public class MaasStrategyModule implements   ActivityEndEventHandler,PlanStrategyModule{

	public static final String StrategyAttributeName = "MAAS_Plan";
	public LinkedHashMap<String,MAASPackage> maasPackages= new LinkedHashMap<>();
	private Random rnd;
		
	public MaasStrategyModule(MAASPackages packages){
		this.maasPackages = new LinkedHashMap<>(packages.getMassPackages());
		rnd = new Random();
	}
	
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
		String currentPlan = (String) plan.getAttributes().getAttribute(StrategyAttributeName);
		if(currentPlan==null) {
			plan.getAttributes().putAttribute(StrategyAttributeName,MAASKeys.get(rnd.nextInt(MAASKeys.size())));
		}else {
			boolean repeat = true;
			int ind=0;
			while(repeat) {
				ind=rnd.nextInt(MAASKeys.size()+1);
				if(ind == MAASKeys.size() || !currentPlan.equals(MAASKeys.get(ind))) {
					repeat = false;
				}
			}
			if(ind == MAASKeys.size()) {
				plan.getAttributes().removeAttribute(StrategyAttributeName);
			}else {
				plan.getAttributes().putAttribute(StrategyAttributeName,MAASKeys.get(ind));
			}
		}
		
		
	}

	@Override
	public void finishReplanning() {
		// For actual execution of replanning 
		
	}

}
