import com.atguigu.tingshu.ServiceSearchApplication;
import com.atguigu.tingshu.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ServiceSearchApplication.class)
public class ServiceSearchTest {

    @Autowired
    private SearchService searchService;


    @Test
    public void testUpperAlbum() {
        for (long i = 0; i <= 1625; i++) {
            try {
                searchService.upperAlbum(i);
            } catch (Exception e) {
                continue;
            }
        }
    }
}
