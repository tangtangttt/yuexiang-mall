package cn.hollis.llm.mentor.rag.service;

import cn.hollis.llm.mentor.rag.model.DirectorMoviesDto;
import cn.hollis.llm.mentor.rag.repo.MovieGraphRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphService {

    @Autowired
    private MovieGraphRepository repository;

    public String retrieveContext(String movieName) {
        List<DirectorMoviesDto> results = repository.findOtherMoviesBySameDirector(movieName);

        if (results.isEmpty()) {
            return "未找到导演过《" + movieName + "》的导演的其他作品。";
        }

        StringBuilder sb = new StringBuilder();
        for (DirectorMoviesDto dto : results) {
            String director = dto.getDirector();
            List<String> movies = dto.getOtherMovies();
            sb.append(String.format("- 导演 %s 还执导了：%s\n", director, String.join("、", movies)));
        }
        return sb.toString().trim();

    }

}