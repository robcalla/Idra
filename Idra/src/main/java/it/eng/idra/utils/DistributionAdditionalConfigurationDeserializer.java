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
package it.eng.idra.utils;

import java.lang.reflect.Type;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import it.eng.idra.beans.DistributionAdditionalConfiguration;
import it.eng.idra.beans.odms.ODMSCatalogueAdditionalConfiguration;
import it.eng.idra.beans.orion.OrionCatalogueConfiguration;
import it.eng.idra.beans.orion.OrionDistributionConfig;
import it.eng.idra.beans.sparql.SparqlCatalogueConfiguration;
import it.eng.idra.beans.sparql.SparqlDistributionConfig;

public class DistributionAdditionalConfigurationDeserializer implements JsonDeserializer<DistributionAdditionalConfiguration>,JsonSerializer<DistributionAdditionalConfiguration>{

	@Override
	public DistributionAdditionalConfiguration deserialize(JsonElement arg0, Type arg1, JsonDeserializationContext arg2)
			throws JsonParseException {
		// TODO Auto-generated method stub
		JSONObject j = new JSONObject(arg0.toString());
		String nodeID=j.optString("nodeID","");	
		if(j.has("orionQuery")) {
			String orionQuery=j.optString("orionQuery","");
			String fiwareService=j.optString("fiwareService","");
			String fiwareServicePath=j.optString("fiwareServicePath","");		
			return new OrionDistributionConfig(orionQuery,fiwareService,fiwareServicePath,nodeID);
		}else if(j.has("sparqlQuery")) {
			String sparqlQuery=j.optString("sparqlQuery","");
			String formats=j.optString("formats","");
			return new SparqlDistributionConfig(sparqlQuery,formats,nodeID);
		}
			return null;
	}

	@Override
	public JsonElement serialize(DistributionAdditionalConfiguration arg0, Type arg1, JsonSerializationContext arg2) {
		// TODO Auto-generated method stub
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("id", arg0.getId());
		jsonObject.addProperty("type", arg0.getType());
		jsonObject.addProperty("nodeID", arg0.getNodeID());
		if(arg0.getType().toLowerCase().equals("orion")) {
			OrionDistributionConfig c = (OrionDistributionConfig) arg0;
			jsonObject.addProperty("query", c.getQuery());
			jsonObject.addProperty("fiwareService", c.getFiwareService());
			jsonObject.addProperty("fiwareServicePath", c.getFiwareServicePath());
		}else if(arg0.getType().toLowerCase().equals("sparql")) {
			SparqlDistributionConfig c = (SparqlDistributionConfig) arg0;
			jsonObject.addProperty("query", c.getQuery());
			jsonObject.addProperty("formats", c.getFormats());
		}
		return jsonObject;
	}

}
