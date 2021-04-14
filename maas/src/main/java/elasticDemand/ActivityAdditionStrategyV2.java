package elasticDemand;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Provider;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ChangeModeConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.modules.ChangeLegMode;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacilities;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class ActivityAdditionStrategyV2 implements PlanStrategy{

	private final PlanStrategy planStrategyDelegate;
	
	private Map<String,Double> activityDurationMap = new HashMap<>();
	private Set<String> unmodifiableActivities;
	private Set<String> actsToInsert = new HashSet<>();
	public static final Logger logger = Logger.getLogger(ActivityAdditionStrategy.class);
	private Map<String,Map<Id<Node>,Set<Coord>>>tpusbActCoords = null;
	private Map<String,Map<Id<Node>,Double>> ActvityToZoneAttractionMap = null;
	private Network tpusbNet = null;
	private DrawFromChoice draw= null;
	private boolean ifUseTypicalDurationFromConfig = false;
	
	@Inject // The constructor must be annotated so that the framework knows which one to use.
	ActivityAdditionStrategyV2(Config config,Scenario scenario, EventsManager eventsManager,PlansConfigGroup plansConfigGroup, ActivityAdditionConfigGroup actConfig,
			TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup, GlobalConfigGroup globalConfigGroup, ChangeModeConfigGroup changeLegModeConfigGroup, ActivityFacilities activityFacilities, Provider<TripRouter> tripRouterProvider) {
		// A PlanStrategy is something that can be applied to a Person (not a Plan).
        // It first selects one of the plans:
        //MyPlanSelector plsanSelector = new MyPlanSelector();
        this.actsToInsert = this.readOneColumnCSV(actConfig.getActToInsertInputFile());
        if(this.actsToInsert == null)throw new IllegalArgumentException("Activity to insert file cannot be null. At least write the recreation activitiy types in each rows of a csv file and point to that file.");
        if(actConfig.getUnmodifiableActInputFile()!=null)this.unmodifiableActivities = this.readOneColumnCSV(actConfig.getUnmodifiableActInputFile());
        boolean collectUnmodifiableActs = false;
        if(this.unmodifiableActivities == null) {
        	unmodifiableActivities= new HashSet<>();
        	collectUnmodifiableActs = true;
        }
        tpusbNet = NetworkUtils.readNetwork(actConfig.getLocationAggregationNetworkFile());
        
        
        tpusbActCoords = new HashMap<>();//this is only needed for the act to be inserted
        ActvityToZoneAttractionMap = new HashMap<>();//this is only needed for the act to be inserted
        Map<String,Tuple<Double,Double>> actTotatlDurationAndCountHolder = new HashMap<>();
        Map<String,Double> totalActivityPerformerCount = new HashMap<>();
        
        // the plan selector may, at the same time, collect events:
        //eventsManager.addHandler(planSelector);

        // if you just want to select plans, you can stop here.
		
		for (Entry<Id<Person>, ? extends Person> p:scenario.getPopulation().getPersons().entrySet()){
			for(Plan pl:p.getValue().getPlans()){
				
				for(PlanElement pe:pl.getPlanElements()){
					if(pe instanceof Activity) {
						Activity act = (Activity)pe;
						Id<Node> currentTPUSB = NetworkUtils.getNearestNode(tpusbNet, act.getCoord()).getId();
						if(collectUnmodifiableActs && (act.getType().contains("work")||act.getType().contains("School"))) {
							this.unmodifiableActivities.add(act.getType());
						}
						
						if(this.actsToInsert.contains(act.getType())) {
							
							if(!tpusbActCoords.containsKey(act.getType()))tpusbActCoords.put(act.getType(), new HashMap<>());
							if(!tpusbActCoords.get(act.getType()).containsKey(currentTPUSB))tpusbActCoords.get(act.getType()).put(currentTPUSB, new HashSet<>());
							tpusbActCoords.get(act.getType()).get(currentTPUSB).add(act.getCoord());
							
							
							if(!ActvityToZoneAttractionMap.containsKey(act.getType()))ActvityToZoneAttractionMap.put(act.getType(),new HashMap<>());
							ActvityToZoneAttractionMap.get(act.getType()).compute(currentTPUSB, (k,v)->v==null?1:v+1);
							totalActivityPerformerCount.compute(act.getType(), (k,v)->v==null?1:v+1);
						}
							
						OptionalTime typicalD = config.planCalcScore().getOrCreateScoringParameters(PopulationUtils.getSubpopulation(p.getValue())).getActivityParams(act.getType()).getTypicalDuration();
						double t = 0;
						if(typicalD.isUndefined()||ifUseTypicalDurationFromConfig==false) {
							if(actTotatlDurationAndCountHolder.get(act.getType())== null)actTotatlDurationAndCountHolder.put(act.getType(),new Tuple<Double,Double>(0.,0.));
							Tuple<Double,Double> oldCount = actTotatlDurationAndCountHolder.get(act.getType());
							double currentDuration = 60.;
							if(act.getStartTime().isDefined() && act.getEndTime().isDefined()) {
								currentDuration = act.getEndTime().seconds()-act.getStartTime().seconds(); 
							}else if(!act.getStartTime().isDefined()&& act.getEndTime().isDefined()) {
								currentDuration = act.getEndTime().seconds(); 
							}else if(act.getStartTime().isDefined()&& !act.getEndTime().isDefined()) {
								currentDuration = 27*3600-act.getStartTime().seconds(); 
							}
							actTotatlDurationAndCountHolder.put(act.getType(),new Tuple<Double,Double>(oldCount.getFirst()+currentDuration,oldCount.getSecond()+1));
							t = actTotatlDurationAndCountHolder.get(act.getType()).getFirst()/actTotatlDurationAndCountHolder.get(act.getType()).getSecond();
						}else {
							t = typicalD.seconds();
						}
						this.activityDurationMap.put(act.getType(),t);
							
							
					}
				};
			};
		};
		
		
//		ActvityToZoneAttractionMap.entrySet().forEach(e->{
//			e.getValue().keySet().forEach(k->e.getValue().compute(k, (c,v)->v=v/totalActivityPerformerCount.get(e.getKey())));
//		});
		
        // Otherwise, to do something with that plan, one needs to add modules into the strategy.  If there is at least
        // one module added here, then the plan is copied and then modified.
		PlanStrategyModule mod = new ActivityAdditionStrategyModuleV3(this.ActvityToZoneAttractionMap,this.tpusbActCoords,this.activityDurationMap,this.unmodifiableActivities,tpusbNet,scenario, actConfig.getDrawMethodFromChoicePool(),actConfig);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector());
		//PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new ExpBetaPlanSelector(config.planCalcScore()));
        builder.addStrategyModule(mod);
//      builder.addStrategyModule(new TripsToLegsModule(tripRouterProvider, globalConfigGroup));
        
		builder.addStrategyModule(new TimeAllocationMutatorModule(tripRouterProvider, plansConfigGroup, timeAllocationMutatorConfigGroup, globalConfigGroup ));
		builder.addStrategyModule(new TimeAllocationMutatorModule(tripRouterProvider, plansConfigGroup, timeAllocationMutatorConfigGroup, globalConfigGroup ));

       // builder.addStrategyModule(new ChangeLegMode(globalConfigGroup, changeLegModeConfigGroup));
		builder.addStrategyModule(new ReRoute(activityFacilities, tripRouterProvider, globalConfigGroup));
        // these modules may, at the same time, be events listeners (so that they can collect information):
        //eventsManager.addHandler(mod);

        // these modules may, at the same time, be events listeners (so that they can collect information):
        //eventsManager.addHandler(mod);

        
        planStrategyDelegate = builder.build();
        
        
	}
	
	

	@Override
	public void run(HasPlansAndId<Plan, Person> person) {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.run(person);
	}

	@Override
	public void init(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.init(replanningContext);
	}

	@Override
	public void finish() {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.finish();
	}
	
	private Set<String> readOneColumnCSV(String input){
		Set<String> outSet = new HashSet<>();
		try {
			BufferedReader bf = new BufferedReader(new FileReader(new File(input)));
			bf.readLine();//get rid of the header. 
			String line = null;
			while((line = bf.readLine())!=null) {
				outSet.add(line);
			}
			bf.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			logger.debug(input + " file not found. Returning null.");
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outSet;
	}


}
