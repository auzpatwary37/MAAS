package optimizerAgent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;



public class transitGenerator {
	private static HashMap<Integer,String> AlphMat=new HashMap<>();
	
	
	public static Tuple<TransitSchedule,Vehicles> createTransit(Scenario scenario, Network network) {
		char p='A';
		for(int q=1;q<8;q++) {
			AlphMat.put(q, String.valueOf(p)+String.valueOf(p)+String.valueOf(p));
			p++;
		}
		
		
		Vehicles tsv=scenario.getTransitVehicles();
		VehiclesFactory tsvf=tsv.getFactory();
		TransitSchedule ts=scenario.getTransitSchedule();
		TransitScheduleFactory tsf=ts.getFactory();
		HashMap<String,TransitStopFacility> transitStopFacilities=new HashMap<>();
		char j='A';
		for(int i=1;i<=7;i++) {
			TransitStopFacility stop=tsf.createTransitStopFacility(Id.create(i+"_"+i+"r"+j+j+j+"_stop", TransitStopFacility.class), network.getNodes().get(Id.createNodeId(i+"r")).getCoord(),false);
			stop.setLinkId(Id.createLinkId(i+"_"+i+"r_stop"));
			stop.setName("Station"+i);
			transitStopFacilities.put(stop.getId().toString(), stop);
			ts.addStopFacility(stop);
			
			stop=tsf.createTransitStopFacility(Id.create(i+"r_"+i+j+j+j+"_stop", TransitStopFacility.class), network.getNodes().get(Id.createNodeId(i+"r")).getCoord(),false);
			stop.setLinkId(Id.createLinkId(i+"r_"+i+"_stop"));
			stop.setName("Station"+i);
			transitStopFacilities.put(stop.getId().toString(), stop);
			ts.addStopFacility(stop);
			j++;
		}
		j='A';
		for(int i=1;i<=7;i++) {
			TransitStopFacility stop=tsf.createTransitStopFacility(Id.create(i+"_"+i+"r"+j+j+j+"_stop_train", TransitStopFacility.class), network.getNodes().get(Id.createNodeId(i+"r")).getCoord(),false);
			stop.setLinkId(Id.createLinkId(i+"_"+i+"r_stop_train"));
			stop.setName("Station"+i+"_train");
			transitStopFacilities.put(stop.getId().toString(), stop);
			ts.addStopFacility(stop);
			
			stop=tsf.createTransitStopFacility(Id.create(i+"r_"+i+j+j+j+"_stop_train", TransitStopFacility.class), network.getNodes().get(Id.createNodeId(i+"r")).getCoord(),false);
			stop.setLinkId(Id.createLinkId(i+"r_"+i+"_stop_train"));
			stop.setName("Station"+i+"_train");
			transitStopFacilities.put(stop.getId().toString(), stop);
			ts.addStopFacility(stop);
			j++;
		}
		
		
		TransitLine tl1=tsf.createTransitLine(Id.create("bus1", TransitLine.class));
		int[] nodes1={1,2,5,6,4};
		List<TransitRouteStop> stops1=new ArrayList<>();
		List<TransitRouteStop> stops1opp=new ArrayList<>();
		for(int i=0;i<nodes1.length;i++) {
			
			stops1.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes1[i]+"_"+nodes1[i]+"r"+AlphMat.get(nodes1[i])+"_stop"), 900*i+30*i, 900*i+30*(i+1)));
			stops1opp.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes1[nodes1.length-i-1]+"r_"+nodes1[nodes1.length-i-1]+AlphMat.get(nodes1[nodes1.length-i-1])+"_stop"), 600*i+30*i, 600*i+30*(i+1)));
		}
		
		NetworkRoute[] nrs=createNetworkRoute(nodes1);
		TransitRoute Route1=tsf.createTransitRoute(Id.create("From_1_to_4_bus1",TransitRoute.class), nrs[0],stops1, "bus");
		TransitRoute Route2=tsf.createTransitRoute(Id.create("From_4_to_1_bus1",TransitRoute.class), nrs[1],stops1opp, "bus");
		
		for(int i=0;i<=3600*24;i=i+600) {
			Departure d=tsf.createDeparture(Id.create(Route1.getId().toString()+"_"+i, Departure.class), i);
			Vehicle v=createTransitVehicle(tsvf,d.getId().toString(),"bus");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			if(!tsv.getVehicleTypes().containsKey(v.getType().getId())) {
				tsv.addVehicleType(v.getType());
			}
			tsv.addVehicle(v);
			Route1.addDeparture(d);
			
			d=tsf.createDeparture(Id.create(Route2.getId().toString()+"_"+i, Departure.class), i);
			v=createTransitVehicle(tsvf,d.getId().toString(),"bus");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route2.addDeparture(d);
		}
		
		tl1.addRoute(Route1);
		tl1.addRoute(Route2);
		ts.addTransitLine(tl1);
		
		TransitLine tl2=tsf.createTransitLine(Id.create("bus2", TransitLine.class));
		List<TransitRouteStop> stops2=new ArrayList<>();
		List<TransitRouteStop> stops2opp=new ArrayList<>();
		int[] nodes2={2,5,7,6,3};
		
		for(int i=0;i<nodes2.length;i++) {
			
			stops2.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes2[i]+"_"+nodes2[i]+"r"+AlphMat.get(nodes2[i])+"_stop"), 900*i+30*i, 900*i+30*(i+1)));
			stops2opp.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes2[nodes2.length-i-1]+"r_"+nodes2[nodes2.length-i-1]+AlphMat.get(nodes2[nodes2.length-i-1])+"_stop"), 600*i+30*i, 600*i+30*(i+1)));
		}
		
		nrs=createNetworkRoute(nodes2);
		TransitRoute Route3=tsf.createTransitRoute(Id.create("From_2_to_3_bus2",TransitRoute.class), nrs[0],stops2, "bus");
		TransitRoute Route4=tsf.createTransitRoute(Id.create("From_3_to_2_bus2",TransitRoute.class), nrs[1],stops2opp, "bus");
		
		for(int i=0;i<=3600*24;i=i+900) {
			Departure d=tsf.createDeparture(Id.create(Route3.getId().toString()+"_"+i, Departure.class), i);
			Vehicle v=createTransitVehicle(tsvf,d.getId().toString(),"bus");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route3.addDeparture(d);
			
			d=tsf.createDeparture(Id.create(Route4.getId().toString()+"_"+i, Departure.class), i);
			v=createTransitVehicle(tsvf,d.getId().toString(),"bus");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route4.addDeparture(d);
		}
		
		tl2.addRoute(Route3);
		tl2.addRoute(Route4);
		ts.addTransitLine(tl2);
		
		
		
		TransitLine tl3=tsf.createTransitLine(Id.create("MTR1", TransitLine.class));
		int[] nodes3={1,5,6,4};
		List<TransitRouteStop> stops3=new ArrayList<>();
		List<TransitRouteStop> stops3opp=new ArrayList<>();
		for(int i=0;i<nodes3.length;i++) {
			
			stops3.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes3[i]+"_"+nodes3[i]+"r"+AlphMat.get(nodes3[i])+"_stop_train"), 600*i+30*i, 600*i+30*(i+1)));
			stops3opp.add(tsf.createTransitRouteStop(transitStopFacilities.get(nodes3[nodes3.length-i-1]+"r_"+nodes3[nodes3.length-i-1]+AlphMat.get(nodes3[nodes3.length-i-1])+"_stop_train"), 600*i+30*i, 600*i+30*(i+1)));
		}
		nrs=createMtrNetworkRoute(nodes3);
		
		TransitRoute Route5=tsf.createTransitRoute(Id.create("From_1_to_4_MTR1",TransitRoute.class), nrs[0],stops3, "train");
		TransitRoute Route6=tsf.createTransitRoute(Id.create("From_4_to_1_MTR1",TransitRoute.class), nrs[1],stops3opp, "train");
		
		for(int i=0;i<=3600*24;i=i+360) {
			Departure d=tsf.createDeparture(Id.create(Route5.getId().toString()+"_"+i, Departure.class), i);
			Vehicle v=createTransitVehicle(tsvf,d.getId().toString(),"MTR");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			if(!tsv.getVehicleTypes().containsKey(v.getType().getId())) {
				tsv.addVehicleType(v.getType());
			}
			tsv.addVehicle(v);
			Route5.addDeparture(d);
			 
			d=tsf.createDeparture(Id.create(Route6.getId().toString()+"_"+i, Departure.class), i);
			v=createTransitVehicle(tsvf,d.getId().toString(),"MTR");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route6.addDeparture(d);
		}
		
		tl3.addRoute(Route5);
		tl3.addRoute(Route6);
		ts.addTransitLine(tl3);
		
		
		

		TransitLine tl4=tsf.createTransitLine(Id.create("MTR2", TransitLine.class));
		int[] nodes4={2,5,7,6,3};
		List<TransitRouteStop> stops4=new ArrayList<>();
		List<TransitRouteStop> stops4opp=new ArrayList<>();
		for(int i=0;i<nodes4.length;i++) {
			TransitStopFacility tsf1=transitStopFacilities.get(nodes4[i]+"_"+nodes4[i]+"r"+AlphMat.get(nodes4[i])+"_stop_train");
			TransitStopFacility tsf2=transitStopFacilities.get(nodes4[nodes4.length-i-1]+"r_"+nodes4[nodes4.length-i-1]+AlphMat.get(nodes4[nodes4.length-i-1])+"_stop_train");
			stops4.add(tsf.createTransitRouteStop(tsf1, 600*i+30*i, 600*i+30*(i+1)));
			stops4opp.add(tsf.createTransitRouteStop(tsf2, 600*i+30*i, 600*i+30*(i+1)));
		}
		nrs=createMtrNetworkRoute(nodes4);
		
		TransitRoute Route7=tsf.createTransitRoute(Id.create("From_2_to_3_MTR2",TransitRoute.class), nrs[0],stops4, "train");
		TransitRoute Route8=tsf.createTransitRoute(Id.create("From_3_to_2_MTR2",TransitRoute.class), nrs[1],stops4opp, "train");
		
		for(int i=0;i<=3600*24;i=i+480) {
			Departure d=tsf.createDeparture(Id.create(Route7.getId().toString()+"_"+i, Departure.class), i);
			Vehicle v=createTransitVehicle(tsvf,d.getId().toString(),"MTR");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route7.addDeparture(d);
			
			d=tsf.createDeparture(Id.create(Route8.getId().toString()+"_"+i, Departure.class), i);
			v=createTransitVehicle(tsvf,d.getId().toString(),"MTR");
			d.setVehicleId(Id.createVehicleId(d.getId().toString()));
			tsv.addVehicle(v);
			Route8.addDeparture(d);
		}
		
		tl4.addRoute(Route7);
		tl4.addRoute(Route8);
		ts.addTransitLine(tl4);
		
		return new Tuple<TransitSchedule,Vehicles>(ts,tsv);
		
	}
	
	private static NetworkRoute[] createNetworkRoute(int[] NodeId) {
		NetworkRoute[] nrs=new NetworkRoute[2];
		List<Id<Link>> links=new ArrayList<>();
		links.add(Id.createLinkId(NodeId[0]+"_"+NodeId[0]+"r_stop"));
		for(int i=0;i<NodeId.length-1;i++) {
			links.add(Id.createLinkId(NodeId[i]+"r_"+NodeId[i+1]));
			links.add(Id.createLinkId(NodeId[i+1]+"_"+NodeId[i+1]+"r_stop"));
		}
		Id<Link>StartLinkId=links.get(0);
		Id<Link>EndLinkId=links.get(links.size()-1);
		links.remove(links.size()-1);
		links.remove(0);
		
		NetworkRoute nr=RouteUtils.createLinkNetworkRouteImpl(StartLinkId,links ,EndLinkId);

		nrs[0]=nr;
		
		links.clear();
		
		links.add(Id.createLinkId(NodeId[NodeId.length-1]+"r_"+NodeId[NodeId.length-1]+"_stop"));
		for(int i=NodeId.length-1;i>0;i--) {
			links.add(Id.createLinkId(NodeId[i]+"_"+NodeId[i-1]+"r"));
			links.add(Id.createLinkId(NodeId[i-1]+"r_"+NodeId[i-1]+"_stop"));
		}
		StartLinkId=links.get(0);
		EndLinkId=links.get(links.size()-1);
		links.remove(links.size()-1);
		links.remove(0);
		
		nr=RouteUtils.createLinkNetworkRouteImpl(StartLinkId,links ,EndLinkId);

		nrs[1]=nr;

		return nrs;
	}
	
	private static NetworkRoute[] createMtrNetworkRoute(int[] nodes4) {
		NetworkRoute[] nrs=new NetworkRoute[2];
		List<Id<Link>> links=new ArrayList<>();
		links.add(Id.createLinkId(nodes4[0]+"_"+nodes4[0]+"r_stop_train"));
		for(int i=0;i<nodes4.length-1;i++) {
			links.add(Id.createLinkId(nodes4[i]+"r_"+nodes4[i+1]+"_MTR"));
			links.add(Id.createLinkId(nodes4[i+1]+"_"+nodes4[i+1]+"r_stop_train"));
		}
		Id<Link>StartLinkId=links.get(0);
		Id<Link>EndLinkId=links.get(links.size()-1);
		links.remove(links.size()-1);
		links.remove(0);
		
		NetworkRoute nr=RouteUtils.createLinkNetworkRouteImpl(StartLinkId,links ,EndLinkId);

		nrs[0]=nr;
		
		links.clear();
		
		links.add(Id.createLinkId(nodes4[nodes4.length-1]+"r_"+nodes4[nodes4.length-1]+"_stop_train"));
		for(int i=nodes4.length-1;i>0;i--) {
			links.add(Id.createLinkId(nodes4[i]+"_"+nodes4[i-1]+"r"));
			links.add(Id.createLinkId(nodes4[i-1]+"r_"+nodes4[i-1]+"_stop_train"));
		}
		StartLinkId=links.get(0);
		EndLinkId=links.get(links.size()-1);
		links.remove(links.size()-1);
		links.remove(0);
		
		nr=RouteUtils.createLinkNetworkRouteImpl(StartLinkId,links ,EndLinkId);

		nrs[1]=nr;

		return nrs;
	}
	
	

	public static ZonalFareCalculator createBusFareCalculator(TransitSchedule ts,List<String> busFareFiles) throws IOException {
		if(AlphMat.size()==0) {
			char p='A';
			for(int q=1;q<8;q++) {
				AlphMat.put(q, String.valueOf(p)+String.valueOf(p)+String.valueOf(p));
				p++;
			}
		}
		
		ZonalFareCalculator busFareCalc=new ZonalFareCalculator(ts);
		BufferedReader bus1FareReader=new BufferedReader(new FileReader(new File(busFareFiles.get(0))));
		BufferedReader bus2FareReader=new BufferedReader(new FileReader(new File(busFareFiles.get(1))));
		bus1FareReader.readLine();
		bus2FareReader.readLine();
		String line;
		HashMap<Id<TransitStopFacility>,HashMap<Id<TransitStopFacility>,Double>> Farebus1=new HashMap<>();
		HashMap<Id<TransitStopFacility>,HashMap<Id<TransitStopFacility>,Double>> Farebus2=new HashMap<>();
		while((line=bus1FareReader.readLine())!=null) {
			String[] part=line.split(",");
			Id<TransitStopFacility> startId=Id.create(part[0].trim()+AlphMat.get(Integer.parseInt(part[0].substring(0, 1)))+"_stop", TransitStopFacility.class);
			Id<TransitStopFacility> EndId=Id.create(part[1].trim()+AlphMat.get(Integer.parseInt(part[1].substring(0, 1)))+"_stop", TransitStopFacility.class);
			double fare=Double.parseDouble(part[2].trim());
			if(Farebus1.containsKey(startId)) {
				Farebus1.get(startId).put(EndId, fare);
			}else {
				Farebus1.put(startId, new HashMap<Id<TransitStopFacility>,Double>());
				Farebus1.get(startId).put(EndId, fare);
			}
		}
		while((line=bus2FareReader.readLine())!=null) {
			String[] part=line.split(",");
			Id<TransitStopFacility> startId=Id.create(part[0].trim()+AlphMat.get(Integer.parseInt(part[0].substring(0, 1)))+"_stop", TransitStopFacility.class);
			Id<TransitStopFacility> EndId=Id.create(part[1].trim()+AlphMat.get(Integer.parseInt(part[1].substring(0, 1)))+"_stop", TransitStopFacility.class);
			double fare=Double.parseDouble(part[2].trim());
			if(Farebus2.containsKey(startId)) {
				Farebus2.get(startId).put(EndId, fare);
			}else {
				Farebus2.put(startId, new HashMap<Id<TransitStopFacility>,Double>());
				Farebus2.get(startId).put(EndId, fare);
			}
		}
		
		
		TransitLine Bus1=ts.getTransitLines().get(Id.create("bus1", TransitLine.class));
		
		busFareCalc.setFullFare(Bus1.getId(), 5.3);
		for(TransitRoute tr:Bus1.getRoutes().values()) {
			busFareCalc.addRoute(Bus1.getId(), tr.getId(), 5.3);
			for(int i=0;i<tr.getStops().size();i++ ){
				for(int j=i;j<tr.getStops().size();j++) {
					if(i==j) {continue;}
					TransitRouteStop ts1=tr.getStops().get(i);
					TransitRouteStop ts2=tr.getStops().get(j);
					double fare=Farebus1.get(ts1.getStopFacility().getId()).get(ts2.getStopFacility().getId());
					Id<TransitLine> lineId=Bus1.getId();
					Id<TransitRoute> routeId=tr.getId();
					Id<TransitStopFacility> startStopId=ts1.getStopFacility().getId();
					Id<TransitStopFacility> endStopId=ts2.getStopFacility().getId();
 					
					busFareCalc.addSectionFare(lineId,routeId ,startStopId,1, 
							endStopId, 1,fare );
				}
			}
		}
		TransitLine Bus2=ts.getTransitLines().get(Id.create("bus2", TransitLine.class));
		busFareCalc.setFullFare(Bus2.getId(), 4.7);
		for(TransitRoute tr:Bus2.getRoutes().values()) {
			busFareCalc.addRoute(Bus2.getId(),tr.getId(), 4.7);
			for(int i=0;i<tr.getStops().size();i++ ){
				for(int j=i;j<tr.getStops().size();j++) {
					if(i==j) {continue;}
					TransitRouteStop ts1=tr.getStops().get(i);
					TransitRouteStop ts2=tr.getStops().get(j);
					double fare=Farebus2.get(ts1.getStopFacility().getId()).get(ts2.getStopFacility().getId());
					Id<TransitLine> lineId=Bus2.getId();
					Id<TransitRoute> routeId=tr.getId();
					Id<TransitStopFacility> startStopId=ts1.getStopFacility().getId();
					Id<TransitStopFacility> endStopId=ts2.getStopFacility().getId();
 					
					busFareCalc.addSectionFare(lineId,routeId ,startStopId,1, 
							endStopId, 1,fare );
				}
			}
		}
		bus1FareReader.close();
		bus2FareReader.close();
		return busFareCalc;
		
	}
	
	/**
	 * 
	 * @param Id: vehicle Id
	 * @param type: Type is only either bus or MTR
	 * @return
	 */
	private static Vehicle createTransitVehicle(VehiclesFactory vsf,String vehicleId,String type) {
		VehicleType vt=vsf.createVehicleType(Id.create(type,VehicleType.class));
		if(type.equals("bus")) {
			
			vt.getCapacity().setSeats(60);
			vt.getCapacity().setStandingRoom(20);
			vt.setPcuEquivalents(3);

		}else {
			VehicleCapacity vc=vt.getCapacity();
			vc.setSeats(50);
			vc.setStandingRoom(120);
		}
		Vehicle v=vsf.createVehicle(Id.createVehicleId(vehicleId), vt);
		return v;
	}
}
