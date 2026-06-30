package io.sapl.tutorial;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SaplTutorial01SpringApplicationTests {

    @Autowired
    MockMvc mockMvc;

    private MockHttpSession sessionFor(String username) throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user(username).password("password"))
                                  .andExpect(status().is3xxRedirection())
                                  .andExpect(redirectedUrl("/api/books"))
                                  .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Anonymous users are sent to the login page")
    void anonymousUsersAreSentToLogin() throws Exception {
        mockMvc.perform(get("/api/books")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Bob sees age appropriate books in the collection")
    void bobSeesAgeAppropriateCollection() throws Exception {
        mockMvc.perform(get("/api/books").session(sessionFor("bob")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(3)))
               .andExpect(jsonPath("$[0].id", is(1)))
               .andExpect(jsonPath("$[1].id", is(2)))
               .andExpect(jsonPath("$[2].id", is(3)));
    }

    @Test
    @DisplayName("Alice sees only the youngest book in the collection")
    void aliceSeesOnlyYoungestBookInCollection() throws Exception {
        mockMvc.perform(get("/api/books").session(sessionFor("alice")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(1)))
               .andExpect(jsonPath("$[0].id", is(1)));
    }

    @Test
    @DisplayName("Zoe can read all individual books")
    void zoeCanReadAllIndividualBooks() throws Exception {
        mockMvc.perform(get("/api/books/4").session(sessionFor("zoe")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id", is(4)))
               .andExpect(jsonPath("$.content", is("Space is scary.")));
    }

    @Test
    @DisplayName("Bob gets transformed content when too young")
    void bobGetsTransformedBookWhenTooYoung() throws Exception {
        mockMvc.perform(get("/api/books/4").session(sessionFor("bob")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id", is(4)))
               .andExpect(jsonPath("$.content", is("Spa████████████")));
    }

    @Test
    @DisplayName("Alice gets transformed content when too young")
    void aliceGetsTransformedBookWhenTooYoung() throws Exception {
        mockMvc.perform(get("/api/books/2").session(sessionFor("alice")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id", is(2)))
               .andExpect(jsonPath("$.content", is("Got█████████████████")));
    }
}
