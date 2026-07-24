package com.company.meetinghelper.venue.api;

import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueSummary;
import com.company.meetinghelper.venue.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {
    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    List<VenueSummary> list() {
        return venueService.list();
    }

    @GetMapping("/{id}")
    VenueDetail get(@PathVariable String id) {
        return venueService.get(id);
    }

    @PostMapping
    VenueDetail create(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    VenueDetail update(
            @PathVariable String id,
            @Valid @RequestBody CreateVenueRequest request
    ) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable String id) {
        venueService.delete(id);
    }
}
