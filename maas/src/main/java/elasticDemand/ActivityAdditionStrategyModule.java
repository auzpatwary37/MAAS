package elasticDemand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;

public class ActivityAdditionStrategyModule implements PlanStrategyModule{

	
	private Map<String,Network> activityLocationMap;
	private Map<String,Double> activityDurationMap;
	private Set<String> unmodifiableActivities;
	private Set<Plan> plans;
	
	public ActivityAdditionStrategyModule(Map<String,Network> activityLocationMap, Map<String,Double> activityDurationMap,Set<String>unmodifiableActivities) {
		this.activityLocationMap = activityLocationMap;
		this.activityDurationMap = activityDurationMap;
		this.unmodifiableActivities = unmodifiableActivities;
	}
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		plans = new HashSet<>(); 
	}

	@Override
	public void handlePlan(Plan plan) {
		// TODO Auto-generated method stub
		plans.add(plan);
	}

	@Override
	public void finishReplanning() {
		// TODO Auto-generated method stub
		this.plans.parallelStream().forEach(plan->{
		Random random = new Random();
		List<PlanElement> oldPlanElements = plan.getPlanElements();
		List<PlanElement> newPlanElements = new ArrayList<>();
		boolean removeLeg = false;
		for(int i = 0; i<oldPlanElements.size();i++) {
			if(!(plan.getPlanElements().get(i) instanceof Leg) ||!removeLeg == true) {
				newPlanElements.add(plan.getPlanElements().get(i));
			}
			if(i>1 && i%2==0 && random.nextDouble()<1./oldPlanElements.size() && i<oldPlanElements.size()-2) {
				Activity act = (Activity)oldPlanElements.get(i);
				Activity actNext = (Activity)oldPlanElements.get(i+2);
				Leg leg = (Leg)oldPlanElements.get(i+1);
				List<String> acts = new ArrayList<>(activityDurationMap.keySet());
				acts.remove(act.getType());
				acts.remove(actNext.getType());
				acts.removeAll(this.unmodifiableActivities);
				String actToInsert = acts.get(random.nextInt(acts.size()));
				Coord coord = NetworkUtils.getNearestNode(this.activityLocationMap.get(actToInsert), act.getCoord()).getCoord();
				Activity actNew = PopulationUtils.createActivityFromCoord(actToInsert, coord);
				actNew.setStartTime(act.getEndTime().seconds());
				actNew.setEndTime(act.getEndTime().seconds());
				Leg leg1 = PopulationUtils.createLeg(leg.getMode());
				Leg leg2 = PopulationUtils.createLeg(leg.getMode());
				newPlanElements.add(leg1);
				newPlanElements.add(actNew);
				newPlanElements.add(leg2);
			}
		}
		});
	}
	
	private int[] locateAndSortUnmodifableActivities(List<PlanElement> pes) {
		List<Integer> ua = new ArrayList<>();
		int i = 0;
		for(PlanElement pe:pes){
			if(pe instanceof Activity){
				Activity act = (Activity)pe;
				if(this.unmodifiableActivities.contains(act.getType()))ua.add(i);
			}
			i++;
		}
		int[] a = new int[ua.size()];
		for(int ii = 0;ii<ua.size();ii++)a[ii] = ua.get(ii);
		return a;
	}

}
