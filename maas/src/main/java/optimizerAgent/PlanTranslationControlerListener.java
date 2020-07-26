package optimizerAgent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.population.PopulationUtils;

import com.google.inject.Inject;

public class PlanTranslationControlerListener implements IterationStartsListener, StartupListener, BeforeMobsimListener, AfterMobsimListener{
	
	@Inject 
	private timeBeansWrapper timeBeansWrapped;

	
	@Inject
	private Scenario scenario;
	
	@Inject
	private OutputDirectoryHierarchy controlerIO;
	
	public final String FileName = "maas.csv";
	public final String varFileName  = "vars.csv";
	private Map<Id<Person>,Person> operators = new HashMap<>();
	
	@Inject
	PlanTranslationControlerListener(){
		
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				this.operators.put(p.getId(),p);
			}
		}
	}
	
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
//		for(Person p:scenario.getPopulation().getPersons().values()) {
//			if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
//				if(p.getSelectedPlan().getScore()==null ||p.getSelectedPlan().getScore()==0) {
//					Plan plan = p.getSelectedPlan();
//					if(p.getPlans().size()>1)
//						System.out.println();
//					plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, new SimpleTranslatedPlan(timeBeansWrapped.timeBeans, plan, scenario));
//				}
//			}
//		}
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		String fileNameFull = controlerIO.getOutputFilename(FileName);
		String varFile = controlerIO.getOutputFilename(varFileName);
		File fullFile = new File(fileNameFull);
		FileWriter fwFull = null;
		FileWriter varFw = null;
		try {
			fwFull = new FileWriter(fullFile,true);
			varFw = new FileWriter(new File(varFile),true);
			if(event.getIteration() == 0) {
				fwFull.append("Operator,iteartion,revenue,packageSold,pacakgeTrips,totalTrips\n");
				varFw.append("Operator,iteartion,var_name,varCurrent,varlowerLimit,varUpperLimit,revenue\n");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for(Person p:this.operators.values()) {
		
			Plan plan = p.getSelectedPlan();
			
			try {
				fwFull.append(p.getId().toString()+","+event.getIteration()+","+plan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName)+","+plan.getAttributes().getAttribute(MaaSUtil.PackageSoldKeyName)+","+
						plan.getAttributes().getAttribute(MaaSUtil.PackageTripKeyName)+","+plan.getAttributes().getAttribute(MaaSUtil.operatorTripKeyName)+"\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
				
			for(Object a:plan.getAttributes().getAsMap().values()) {
				if(a instanceof VariableDetails) {
					VariableDetails var = (VariableDetails)a;
					try {
						varFw.append(p.getId().toString()+","+event.getIteration()+","+var.getCurrentValue()+","+var.getLimit().getFirst()+","+var.getLimit().getSecond()+","+plan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName)+"\n");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		
		try {
			varFw.flush();
			fwFull.flush();
			fwFull.close();
			varFw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}



	

}
