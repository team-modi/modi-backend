package modi.backend.infra.exhibition.sync;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.sync.entity.ExternalApiCall;
import modi.backend.domain.exhibition.sync.ExternalApiCallRepository;

/**
 * 외부 호출 감사 어댑터.
 *
 * <p><b>{@code REQUIRES_NEW}가 이 클래스의 핵심이다.</b> 감사는 호출자의 트랜잭션과 <b>생사를 같이하면 안 된다</b>:
 * <ul>
 *   <li><b>사실 보존</b> — 호출은 이미 일어났고 과금도 이미 됐다. 그 뒤 비즈니스 트랜잭션이 롤백된다고 해서
 *       "구글을 부른 적 없음"이 되면 감사 기록이 거짓말을 한다. 우리가 태운 돈은 롤백되지 않는다.</li>
 *   <li><b>오염 차단</b> — 반대 방향이 더 위험하다. 감사 저장이 실패했을 때 호출자의 트랜잭션에 얹혀 있으면
 *       그 트랜잭션이 rollback-only로 마킹돼 <b>본 기능이 통째로 죽는다</b>(예: 상세 조회는 {@code @Transactional}
 *       안에서 detail2를 부른다). 부가 기록이 본 기능을 멈추면 안 된다.</li>
 * </ul>
 * 별도 트랜잭션이라 커넥션을 하나 더 쓰지만, 감사 행은 단건 INSERT라 짧다.
 */
@Repository
@RequiredArgsConstructor
public class ExternalApiCallRepositoryImpl implements ExternalApiCallRepository {

	private final ExternalApiCallJpaRepository jpaRepository;

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ExternalApiCall save(ExternalApiCall externalApiCall) {
		return jpaRepository.save(externalApiCall);
	}
}
