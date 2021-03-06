/*******************************************************************************
 * Idra - Open Data Federation Platform
 *  Copyright (C) 2020 Engineering Ingegneria Informatica S.p.A.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package it.eng.idra.scheduler.job;

import java.util.HashMap;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.json.JSONObject;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;

import it.eng.idra.beans.odms.ODMSCatalogue;
import it.eng.idra.beans.odms.ODMSCatalogueNotFoundException;
import it.eng.idra.beans.odms.ODMSManagerException;
import it.eng.idra.beans.orion.OrionCatalogueConfiguration;
import it.eng.idra.management.ODMSManager;

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class OAUTHTokenSynchJob implements Job{

	public static Logger logger = LogManager.getLogger(OAUTHTokenSynchJob.class);
		
	public OAUTHTokenSynchJob() {}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		try {
			logger.info("Requesting token for catalogue: "+context.getJobDetail().getJobDataMap().get("nodeID"));
			ODMSCatalogue node = ODMSManager.getODMSCatalogue((int) context.getJobDetail().getJobDataMap().get("nodeID"));
			OrionCatalogueConfiguration orionConfig = (OrionCatalogueConfiguration) node.getAdditionalConfig();
			HashMap<String, String> map = retrieveUpdatedToken(orionConfig);
			orionConfig.setAuthToken(map.get("access_token"));
			logger.info("New Token: "+orionConfig.getAuthToken());
			//orionConfig.setRefreshToken(map.get("refresh_token"));
			node.setAdditionalConfig(orionConfig);
			ODMSManager.updateODMSCatalogue(node, true);
			
		} catch (ODMSCatalogueNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ODMSManagerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static HashMap<String,String> retrieveUpdatedToken(OrionCatalogueConfiguration conf) throws Exception {
		Client client = ClientBuilder.newClient();
		
		HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(conf.getClientID(), conf.getClientSecret());
		client.register(feature);
		
		WebTarget webTarget = client.target(conf.getOauth2Endpoint());
		Invocation.Builder invocationBuilder =  webTarget.request(MediaType.APPLICATION_FORM_URLENCODED);
		MultivaluedMap<String, String> formData = new MultivaluedHashMap<String, String>();
	    //formData.add("grant_type", "refresh_token");
	    //formData.add("refresh_token", conf.getRefreshToken());
	    formData.add("grant_type", "client_credentials");
	    
		Response response = invocationBuilder.post(Entity.form(formData));
		
		StatusType status = response.getStatusInfo();
		JSONObject res = new JSONObject(response.readEntity(String.class));
		
		if(status.getStatusCode() == 200){
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("access_token", res.getString("access_token"));
			//map.put("refresh_token",res.getString("refresh_token"));
			return map;
		}else{
			throw new Exception("Error while retrieving the authentication token");
		}	
	}
	
//	public static void main(String args[]) {
//		OrionCatalogueConfiguration c = new OrionCatalogueConfiguration();
//		
//		c.setAuthToken("120McEvGRGQEMX5cVdS2Yi8qL1IHFh");
//		c.setRefreshToken("VK3FZv6NDTbXbWwHLsn4XzdJWViWGD");
//		
//		c.setClientID("ee23f58b3f47459a99ecc2970ae4878b");
//		c.setClientSecret("1eaa8db384ac439abd670d78e2315cc2");
//		
//		c.setOauth2Endpoint("https://services.synchronicity-iot.eu/oauth2/token");
//		
//		try {
//			System.out.println(retrieveUpdatedToken(c));
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
}
