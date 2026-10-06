package co.edu.unisimon.expoideas.users;

/** Una persona del listado, y si ya se registró. */
public record RosterEntryResponse(
        Integer id, String email, Role role, String firstName, String lastName, boolean registered) {

    static RosterEntryResponse from(RosterEntry entry, boolean registered) {
        return new RosterEntryResponse(
                entry.getId(),
                entry.getEmail(),
                entry.getRole(),
                entry.getFirstName(),
                entry.getLastName(),
                registered);
    }
}
