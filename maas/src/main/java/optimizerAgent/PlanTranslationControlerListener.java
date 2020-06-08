package optimizerAgent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
	
	@Inject
	PlanTranslationControlerListener(){
		
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {

	}
	
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				if(p.getSelectedPlan().getScore()==null ||p.getSelectedPlan().getScore()==0) {
					Plan plan = p.getSelectedPlan();
					if(p.getPlans().size()>1)
						System.out.println();
					plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, new SimpleTranslatedPlan(timeBeansWrapped.timeBeans, plan, scenario));
				}
			}
		}
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		String fileName = controlerIO.getIterationFilename(event.getIteration(), FileName);
		File file = new File(fileName);
		FileWriter fw = null;
		try {
			fw= new FileWriter(file);
			fw.append("Operator,revenue,var_name,varCurrent,varlowerLimit,varUpperLimit\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				Plan plan = p.getSelectedPlan();
				for(Entry<String, Object> a:plan.getAttributes().getAsMap().entrySet()) {
					try {
					if(a.getValue() instanceof VariableDetails) {
						
							fw.append(p.getId().toString()+","+""+","+((VariableDetails)a.getValue()).getVariableName()+","+
									((VariableDetails)a.getValue()).getCurrentValue()+","+((VariableDetails)a.getValue()).getLimit().getFirst()+","+((VariableDetails)a.getValue()).getLimit().getSecond()+"\n");
						
					}else if(a.getKey().contains(MaaSUtil.operatorRevenueName)){
						fw.append(p.getId().toString()+","+a.getValue()+","+""+","+
								""+","+""+","+""+"\n");
					}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		try {
			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}



	

}
