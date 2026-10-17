package io.github.serfom256.intentflow.examples.shop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresContainerConfig.class, ApiTestSupport.ScriptedLlmConfig.class })
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected ScriptedLlmClient llm;

    protected MvcTestResult post(String url, String json) {
        return mvc.post().uri(url).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult put(String url, String json) {
        return mvc.put().uri(url).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedLlmConfig {

        @Bean
        ScriptedLlmClient scriptedLlmClient() {
            return new ScriptedLlmClient();
        }
    }
}
