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

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class MaaSStrategyModule implements   ActivityEndEventHandler,PlanStrategyModule{

	
	public LinkedHashMap<String,MaaSPackage> maasPackages= new LinkedHashMap<>();
	private Random rnd;
		
	public MaaSStrategyModule(MaaSPackages packages){
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
		List<String> MaaSKeys=new ArrayList<>(this.maasPackages.keySet());
		String currentMaaSPackage = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		if(currentMaaSPackage==null) {
			plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,MaaSKeys.get(rnd.nextInt(MaaSKeys.size())));
		}else {
			boolean repeat = true;
			int ind=0;
			while(repeat) {
				ind=rnd.nextInt(MaaSKeys.size()+1);
				if(ind == MaaSKeys.size() || !currentMaaSPackage.equals(MaaSKeys.get(ind))) {
					repeat = false;
				}
			}
			if(ind == MaaSKeys.size()) {
				plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			}else {
				plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,MaaSKeys.get(ind));
			}
		}
		
		
	}

	@Override
	public void finishReplanning() {
		// For actual execution of replanning 
		
	}

}
