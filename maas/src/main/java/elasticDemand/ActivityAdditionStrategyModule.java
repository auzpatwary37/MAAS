package elasticDemand;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.router.TripStructureUtils;

import com.google.common.collect.Sets;

import createBus.BusDataExtractor;

public class ActivityAdditionStrategyModule implements PlanStrategyModule{

	
	private Map<String,Set<Coord>> activityLocationMap;
	private Map<Coord,Id<Link>> pointToLinkIdMap;
	private Map<String,Double> activityDurationMap;
	private Set<String> unmodifiableActivities;
	private Set<Plan> plans;
	private Network network;
	
	public ActivityAdditionStrategyModule(Map<String,Set<Coord>> activityLocationMap, Map<String,Double> activityDurationMap,Set<String>unmodifiableActivities, Scenario scenario, Map<Coord,Id<Link>> pointToLinkIdMap) {
		this.activityLocationMap = activityLocationMap;
		this.activityDurationMap = activityDurationMap;
		this.unmodifiableActivities = unmodifiableActivities;
		this.network = scenario.getNetwork();
		this.pointToLinkIdMap = pointToLinkIdMap;
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
		List<Activity> actInPlan = new ArrayList<>();  
		List<PlanElement> oldPlanElements = plan.getPlanElements();
		List<PlanElement> newPlanElements = new ArrayList<>();
		int kk = 0;
		for(PlanElement pl:plan.getPlanElements()){
			
			if(pl instanceof Activity && !((Activity)pl).getType().equals("pt interaction") && kk<plan.getPlanElements().size()-1) {
				actInPlan.add(((Activity)pl));
			}
			kk++;
		};
		String actType = actInPlan.get(random.nextInt(actInPlan.size())).getType();
		boolean removeLeg = false;
		boolean finishActAddition = false;
		double timeShift = 0;
		for(int i = 0; i<oldPlanElements.size();i++) {
			if(!(plan.getPlanElements().get(i) instanceof Leg) ||!removeLeg == true) {
				if(plan.getPlanElements().get(i) instanceof Activity) {
					if(((Activity)plan.getPlanElements().get(i)).getStartTime().isDefined())((Activity)plan.getPlanElements().get(i)).setStartTime(((Activity)plan.getPlanElements().get(i)).getStartTime().seconds()+timeShift);
					if(((Activity)plan.getPlanElements().get(i)).getEndTime().isDefined())((Activity)plan.getPlanElements().get(i)).setEndTime(((Activity)plan.getPlanElements().get(i)).getEndTime().seconds()+timeShift);
				}
				newPlanElements.add(plan.getPlanElements().get(i));
			}else {
				removeLeg = false;
			}
			if(i%2==0 && i<oldPlanElements.size()-2 && finishActAddition == false && ((Activity)oldPlanElements.get(i)).getType().equals(actType)) {
				finishActAddition = true;
				Activity act = (Activity)oldPlanElements.get(i);
				Activity actNext = (Activity)oldPlanElements.get(i+2);
				Leg leg = (Leg)oldPlanElements.get(i+1);
				List<String> acts = new ArrayList<>(activityDurationMap.keySet());
				acts.remove(act.getType());
				acts.remove(actNext.getType());
				acts.removeAll(this.unmodifiableActivities);
				String actToInsert = acts.get(random.nextInt(acts.size()));
				final Coord c = act.getCoord();
				final Coord nearest = Collections.min(this.activityLocationMap.get(actToInsert), new Comparator<Coord>() {

					  public int compare(final Coord p1, final Coord p2) {
						  double d1 = NetworkUtils.getEuclideanDistance(p1, c);
						  double d2 = NetworkUtils.getEuclideanDistance(p2, c);
						  if(d1<d2) {
							  return -1;
						  }else if(d1==d2) {
							  return 0;
						  }else {
							  return 1;
						  }
					  }
					});
				//Coord coord = NetworkUtils.getNearestNode(this.activityLocationMap.get(actToInsert), act.getCoord()).getCoord();
				
				Activity actNew = PopulationUtils.createActivityFromCoord(actToInsert, new Coord(nearest.getX(),nearest.getY()));
				actNew.setLinkId(BusDataExtractor.getNearestLinksExactlyByMath(network, actNew.getCoord(), 
						100.0, Sets.newHashSet(TransportMode.car), 70/3.6, new HashSet<>()).get(0));
				double startTime = 0;
				if(act.getEndTime().isDefined()) {
					startTime = act.getEndTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(act.getCoord(), actNew.getCoord()))/(45*1000/3600);
				}else {
					startTime = ((Leg)oldPlanElements.get(i+1)).getDepartureTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(act.getCoord(), actNew.getCoord()))/(45*1000/3600);
				}
				actNew.setStartTime(startTime);
				actNew.setEndTime(startTime+this.activityDurationMap.get(actNew.getType()));
				timeShift = 1.5*(NetworkUtils.getEuclideanDistance(act.getCoord(), actNew.getCoord()))/(45*1000/3600)+this.activityDurationMap.get(actNew.getType());
				String legMode = leg.getMode();
				if(legMode.equals("transit_walk"))legMode = "pt";
				Leg leg1 = PopulationUtils.createLeg(legMode);
				Leg leg2 = PopulationUtils.createLeg(legMode);
				TripStructureUtils.setRoutingMode(leg1, TripStructureUtils.getRoutingMode(leg));
				TripStructureUtils.setRoutingMode(leg2, TripStructureUtils.getRoutingMode(leg));
				newPlanElements.add(leg1);
				newPlanElements.add(actNew);
				newPlanElements.add(leg2);
				removeLeg = true;
			}
		}
		plan.getPlanElements().clear();
		plan.getPlanElements().addAll(newPlanElements);
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
