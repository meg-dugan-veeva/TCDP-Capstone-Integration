package com.veeva.vault.custom.jobs;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.csv.*;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.http.*;

import java.util.List;

@JobInfo(adminConfigurable = true, isVisible = true)
public class ProductLoaderJob implements Job {

    @Override
    public JobInputSupplier init(JobInitContext context) {
        LogService logService = ServiceLocator.locate(LogService.class);
        logService.info("Initializing Product Catalog Loader Job...");
        
        List<JobItem> jobItems = VaultCollections.newList();
        
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        CsvService csvService = ServiceLocator.locate(CsvService.class);

        String query = "SELECT id FROM documents WHERE name__v = 'products.csv' ORDER BY document_creation_date__v DESC LIMIT 1";
        QueryResponse queryResponse = queryService.query(query);
        
        QueryResult firstResult = queryResponse.streamResults().findFirst().orElse(null);
        if (firstResult == null) {
            logService.error("Failed to run job: Could not find any document named 'products.csv' in the Document Library.");
            return context.newJobInput(jobItems);
        }

        String docId = firstResult.getValue("id", ValueType.STRING);
        logService.info("Found products.csv document with ID: " + docId + ". Initiating local authenticated download call...");

        HttpRequest httpRequest = httpService.newLocalHttpRequest(RequestContextUserType.REQUEST_OWNER);
        httpRequest.setMethod(HttpMethod.GET);
        httpRequest.appendPath("/api/v26.1/objects/documents/" + docId + "/file");
        
        httpService.send(httpRequest, HttpResponseBodyValueType.STRING)
            .onSuccess(httpResponse -> {
                String csvContent = httpResponse.getResponseBody();
                logService.info("Downloaded products.csv content successfully. Parsing rows...");

                CsvData csvData = csvService.readCsv(csvContent);
                for (int i = 0; i < csvData.getNumRows(); i++) {
                    Row row = csvData.getRow(i);
                    JobItem jobItem = context.newJobItem();
                    jobItem.setValue("product_name", row.getValueByName("Product Name", CsvValueType.STRING));
                    jobItem.setValue("product_code", row.getValueByName("Product Code", CsvValueType.STRING));
                    jobItem.setValue("launch_date", row.getValueByName("Launch Date", CsvValueType.STRING));
                    jobItem.setValue("unit_price", row.getValueByName("Unit Price", CsvValueType.STRING));
                    jobItem.setValue("product_family", row.getValueByName("Product Family", CsvValueType.STRING));
                    jobItems.add(jobItem);
                }
            })
            .onError(error -> {
                logService.error("Failed to download products.csv via HTTP loopback: " + error.getMessage());
                throw new RollbackException("DOWNLOAD_FAILED", error.getMessage());
            })
            .execute();
        
        return context.newJobInput(jobItems);
    }

    @Override
    public void process(JobProcessContext context) {
        LogService logService = ServiceLocator.locate(LogService.class);
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        
        List<Record> productsToCreate = VaultCollections.newList();

        for (JobItem item : context.getCurrentTask().getItems()) {
            String name = item.getValue("product_name", JobValueType.STRING);
            String code = item.getValue("product_code", JobValueType.STRING);
            String rawDate = item.getValue("launch_date", JobValueType.STRING);
            String rawPrice = item.getValue("unit_price", JobValueType.STRING);

            java.time.LocalDate date = java.time.LocalDate.parse(rawDate); 
            java.time.LocalDate price = null; 

            Record newProduct = recordService.newRecord("therapeutic_product__c");
            newProduct.setValue("name__v", name);
            newProduct.setValue("external_id__c", code);
            newProduct.setValue("launch_date__c", date);

            productsToCreate.add(newProduct);
        }

        if (!productsToCreate.isEmpty()) {
            recordService.batchSaveRecords(productsToCreate)
                .onSuccesses(results -> logService.info("Successfully loaded " + results.size() + " Products."))
                .onErrors(errors -> {
                    logService.error("Failed to insert Product records. Reason: " + errors.get(0).getError().getMessage());
                    throw new RollbackException("INSERT_FAILED", errors.get(0).getError().getMessage());
                })
                .execute();
        }
    }

    @Override
    public void completeWithSuccess(JobCompletionContext context) {
        LogService logService = ServiceLocator.locate(LogService.class);
        logService.info("Product Catalog Loader Job completed successfully.");
    }

    @Override
    public void completeWithError(JobCompletionContext context) {
        LogService logService = ServiceLocator.locate(LogService.class);
        logService.error("Product Catalog Loader Job failed with errors.");
    }
}
