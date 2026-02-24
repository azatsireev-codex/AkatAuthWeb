package net.akat.auth.integration.python;

import net.akat.auth.repository.FamilyAccessRepository;

public class LocalFamilyAccessGateway implements FamilyAccessGateway {
    private final FamilyAccessRepository repository;

    public LocalFamilyAccessGateway(FamilyAccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public String createGroup(String nickname1, String nickname2) {
        return repository.createGroupWithMembers(nickname1, nickname2);
    }

    @Override
    public void addMember(String groupId, String nickname) {
        repository.addMember(groupId, nickname);
    }

    @Override
    public boolean removeMember(String nickname) {
        return repository.removeMember(nickname);
    }

    @Override
    public int deleteGroup(String groupId) {
        return repository.deleteGroup(groupId);
    }

    @Override
    public boolean areInSameGroup(String nickname1, String nickname2) {
        return repository.areInSameGroup(nickname1, nickname2);
    }
}
