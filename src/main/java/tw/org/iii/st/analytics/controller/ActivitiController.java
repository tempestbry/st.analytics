package tw.org.iii.st.analytics.controller;

import org.springframework.http.*;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Created by ansonliu on 2016/1/14.
 */
@RestController
@RequestMapping("/activiti")
public class ActivitiController {

    @RequestMapping("/history/historic-process-instances/{processInstanceId}/variables/{variableName}/data")
    public Map test(@PathVariable("processInstanceId") String processInstanceId,
                       @PathVariable("variableName") String variableName) {
        Map output = Collections.emptyMap();
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        try {
            headers.add("authorization", "Basic "+ Base64.getEncoder().encodeToString("kermit:kermit".getBytes("UTF-8")));
        }catch (Exception e){

        }
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));

        HttpEntity<String> entity = new HttpEntity<String>(headers);

        ResponseEntity<byte[]> response =
                restTemplate.exchange(
                "http://api.vztaiwan.com:8080/activiti-webapp-rest2/service/history/historic-process-instances/" + processInstanceId + "/variables/" + variableName + "/data/",
                HttpMethod.GET, entity, byte[].class);

        if (response.getStatusCode() == HttpStatus.OK) {


            try {

                ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(response.getBody())));
                output = (HashMap) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
            }


        }


        return output;
    }

}
