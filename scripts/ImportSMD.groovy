/*******************************************************************************
 * Mars import script to convert Single-molecule Dataset (SMD) format to Molecule Archive format
 *
 * Copyright (C) 2022, Duderstadt Lab
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
#@ File file
#@OUTPUT MoleculeArchive archive
 
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import de.mpg.biochem.mars.util.LogBuilder

import java.io.File
import java.io.IOException

import org.scijava.ItemIO
import org.scijava.log.LogService
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsOMEMetadata
import de.mpg.biochem.mars.molecule.SingleMolecule
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive
import de.mpg.biochem.mars.table.MarsTable
import de.mpg.biochem.mars.util.MarsMath

LogBuilder builder = new LogBuilder()

def log = LogBuilder.buildTitleBlock("Single-molecule dataset (SMD) Import Script")
builder.addParameter("File", file.getAbsolutePath())
log += builder.buildParameterList()

ObjectMapper mapper = new ObjectMapper()

JsonNode jsonNode = mapper.readTree(file)
def datasetName = (jsonNode.has("desc")) ? jsonNode.get("desc").asText() : "Converted Single-molecule Dataset"
def datasetId = (jsonNode.has("id")) ? jsonNode.get("id").asText() : MarsMath.getUUID58().substring(0, 10)
def datasetAttr = (jsonNode.has("attr")) ? jsonNode.get("attr").asText() : ""
	
archive = new SingleMoleculeArchive(datasetName)
	
def meta = archive.createMetadata(datasetId)
meta.setNotes(datasetAttr)
archive.putMetadata(meta)
	
JsonNode data = jsonNode.get("data")
for (JsonNode node : data) {
	def record_id = node.get("id").asText()
	def table = new MarsTable(record_id)
	JsonNode index = node.get("index")
	DoubleColumn iCol = new DoubleColumn("index")
	for (JsonNode indexNode : index)
		iCol.add(indexNode.doubleValue())
			
	table.add(iCol)
		
	JsonNode values = node.get("values")
	values.fieldNames().forEachRemaining(field -> {
		DoubleColumn col = new DoubleColumn(field)
		JsonNode array = values.get(field)
		for (JsonNode value : array)
			col.add(value.doubleValue())
		table.add(col)
	})
		
	def molecule = archive.createMolecule(record_id, table)
	molecule.setMetadataUID(datasetId)
	if (node.has("attr")) molecule.setNotes(node.get("attr").asText())
	archive.put(molecule)
}
		
archive.logln(log)
archive.logln(LogBuilder.endBlock(true))