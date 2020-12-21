package it.polito.verefoo.rest.spring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
// import springfox.documentation.swagger2.annotations.EnableSwagger2;

// TODO #jalol separate config from rest api
@SpringBootApplication
// @EnableSwagger2
public class SpringBootConfiguration {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootConfiguration.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {

            /*
             * System.out.println("Let's inspect the beans provided by Spring Boot:");
             * 
             * String[] beanNames = ctx.getBeanDefinitionNames(); Arrays.sort(beanNames);
             * for (String beanName : beanNames) { System.out.println(beanName); }
             */

        };
    }

    @Bean
    public HttpMessageConverters converters() {
        return new HttpMessageConverters(true,
                Arrays.asList(new MappingJackson2HttpMessageConverter(), new Jaxb2RootElementHttpMessageConverter()));
    }

    /*
     * This bean customizes the creation of the openapi UI in Swagger version 3
     */
    @Bean
    public OpenAPI customOpenAPI() {
        
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("version 2");
        tags.add(tag);
        tag = new Tag();
        tag.setName("version 1");
        tags.add(tag);

        List<Server> servers = new ArrayList<>();
        Server server = new Server();
        server.setDescription("ADP module server");
        server.setUrl("http://localhost:8085/verefoo");
        servers.add(server);

        return new OpenAPI()
                .components(new Components())
                .servers(servers)
                .info(new Info().title("Verefoo API documentation").description(
                        "This is the automatically-generated documentation of Verefoo's REST APIs."))
                .tags(tags)
                ;
    }

    @Bean
    public OpenApiCustomiser sortSchemasAlphabetically() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            schemas = schemas.entrySet().stream().sorted((entry1, entry2) -> entry1.getKey().compareTo(entry2.getKey())).collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
            openApi.getComponents().setSchemas(new TreeMap<>(schemas));
        };
    }

    /*
     * @Bean public Docket apiDocket() { return new
     * Docket(DocumentationType.SWAGGER_2) .select()
     * .apis(RequestHandlerSelectors.basePackage("it.polito.verefoo.rest.spring"))
     * .paths(PathSelectors.any()) .build(); }
     */
}