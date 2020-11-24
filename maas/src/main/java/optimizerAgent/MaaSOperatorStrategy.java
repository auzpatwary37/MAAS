package optimizerAgent;

import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.selectors.BestPlanSelector;
import org.matsim.core.replanning.selectors.KeepSelected;
import org.matsim.withinday.controller.ExecutedPlansService;
import org.matsim.withinday.controller.ExecutedPlansServiceImpl;

import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;

public class MaaSOperatorStrategy implements PlanStrategy{
	
	
	

	
	private final PlanStrategy planStrategyDelegate;
	
	@Inject @Nullable 
	private PopulationCompressor compressor;
	
	
	
	
	@Inject // The constructor must be annotated so that the framework knows which one to use.
	MaaSOperatorStrategy(Config config,Scenario scenario, @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages, timeBeansWrapper timeBeans, Map<String,FareCalculator>fareCalculators,
			EventsManager eventsManager, ExecutedPlansServiceImpl executedPlans, OutputDirectoryHierarchy controlerIO, @Nullable PopulationCompressor compressor, @Named("MaaSType") String type) {
		// A PlanStrategy is something that can be applied to a Person (not a Plan).
        // It first selects one of the plans:
        //MyPlanSelector planSelector = new MyPlanSelector();
        

        // the plan selector may, at the same time, collect events:
        //eventsManager.addHandler(planSelector);

        // if you just want to select plans, you can stop here.


        // Otherwise, to do something with that plan, one needs to add modules into the strategy.  If there is at least
        // one module added here, then the plan is copied and then modified.
		this.compressor = compressor;
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new KeepSelected());
        builder.addStrategyModule(new MaaSOperatorStrategyModule(packages, scenario, timeBeans, fareCalculators,executedPlans,controlerIO, compressor, type));

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

}
