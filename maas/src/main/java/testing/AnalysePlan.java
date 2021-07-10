package testing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import optimizerAgent.MaaSUtil;

public class AnalysePlan {
	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
//		
		new PopulationReader(scenario).readFile("toyScenarioLarge\\output_Elastic_May17sepOpnull\\extPopulation.xml");
		Population population = scenario.getPopulation();
//		List<Plan> plans = new ArrayList<>();
//	
//		population.getPersons().values().forEach(p->{
//			
//			plans.addAll(p.getPlans());
//			
//		});
//		boolean[][] ifSame = new boolean[plans.size()][plans.size()];
//		for(int i = 0;i<plans.size();i++) {
//			for(int j = i;j<plans.size();j++) {
//				ifSame[i][j] = MaaSUtil.planEquals(plans.get(i), plans.get(j));
//				ifSame[j][i] = ifSame[i][j];
//			}
//		}
//		System.out.println();
		try {
			RandomAccessFile ra = new RandomAccessFile("toyScenarioLarge\\output_Elastic_May17sepOpnull\\extPopulation.xml","r");
			FileWriter fw = new FileWriter(new File("test/tempFile.txt"));
			for(int i=0;i<10000;i++) {
				fw.append(ra.readLine()+"\n");
				fw.flush();
			}
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int maxPlans = 5;
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(p.getId().toString().equals("32591.0_3.0_2")) {
				System.out.println("");
			}
			MaaSUtil.sortPlanBasedOnUtility((List<Plan>) p.getPlans());
			if(p.getPlans().size()>maxPlans) {
				for(int i=p.getPlans().size()-1;i>=maxPlans;i--) {
					p.getPlans().remove(i);
				}
			}
		}
		Map<String,Integer> maasPac = new HashMap<>();
		for(Entry<Id<Person>, ? extends Person> p:scenario.getPopulation().getPersons().entrySet()){
			for(Plan pl:p.getValue().getPlans()) {
				String maas = (String) pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
				if(maas!=null && maas.equals("ferry"))pl.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
				maasPac.compute(maas, (k,v)->v==null?1:v+1);
			}
		}
		System.out.println(maasPac);
		new PopulationWriter(scenario.getPopulation()).write("test/refinedPop_individualPac1May.xml");
	}

}
