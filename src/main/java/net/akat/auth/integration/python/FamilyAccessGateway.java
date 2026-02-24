package net.akat.auth.integration.python;

public interface FamilyAccessGateway {
    String createGroup(String nickname1, String nickname2);
    void addMember(String groupId, String nickname);
    boolean removeMember(String nickname);
    int deleteGroup(String groupId);
    boolean areInSameGroup(String nickname1, String nickname2);
}
