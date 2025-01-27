package com.alzal.nadeulseoulbackend.domain.curations.service;

import com.alzal.nadeulseoulbackend.domain.curations.dto.*;
import com.alzal.nadeulseoulbackend.domain.curations.entity.*;

import com.alzal.nadeulseoulbackend.domain.curations.exception.CurationNotFoundException;
import com.alzal.nadeulseoulbackend.domain.curations.repository.*;
import com.alzal.nadeulseoulbackend.domain.curations.util.ImageHandler;
import com.alzal.nadeulseoulbackend.domain.mypage.exception.UserNotFoundException;
import com.alzal.nadeulseoulbackend.domain.stores.dto.StoreInfoDto;
import com.alzal.nadeulseoulbackend.domain.stores.entity.StoreInfo;
import com.alzal.nadeulseoulbackend.domain.stores.repository.StoreInfoRepository;
import com.alzal.nadeulseoulbackend.domain.tag.dto.Code;
import com.alzal.nadeulseoulbackend.domain.tag.dto.CodeDto;
import com.alzal.nadeulseoulbackend.domain.tag.dto.CodeRequestDto;
import com.alzal.nadeulseoulbackend.domain.tag.exception.TagNotFoundException;
import com.alzal.nadeulseoulbackend.domain.tag.repository.CodeRepository;
import com.alzal.nadeulseoulbackend.domain.users.entity.User;
import com.alzal.nadeulseoulbackend.domain.users.repository.UserRepository;
import com.alzal.nadeulseoulbackend.domain.users.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class CurationService {

    @Autowired
    private CurationTagRepository curationRepository;

    @Autowired
    private StoreInfoRepository storeInfoRepository;

    @Autowired
    private StoreInCurationRepository storeInCurationRepository;

    @Autowired
    private ImageRepositoroy imageRepositoroy;

    @Autowired
    private CodeRepository codeRepository;

    @Autowired
    private LocalRepository localRepository;

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private ImageHandler imageHandler;

    public CurationResponseDto getCuration(Long curationSeq) {
        Curation curation = curationRepository.findByCurationSeqAndHiddenIsFalse(curationSeq)
                .orElseThrow(()-> new CurationNotFoundException("큐레이션이"));

        curation.addViews(); // 조회수 추가

        List<CodeDto> localDtoList = curation.getLocalCuration().stream().map(LocalCuration::getCode).collect(Collectors.toList())
                .stream().map(CodeDto::fromEntity).collect(Collectors.toList());
        List<CodeDto> themeDtoList = curation.getThemeCuration().stream().map(ThemeCuration::getCode).collect(Collectors.toList())
                .stream().map(CodeDto::fromEntity).collect(Collectors.toList());

        List<Long> fileList = curation.getImageList().stream().map(Image::getImageSeq).collect(Collectors.toList());
        List<StoreInCurationDto> courseInfoList = curation.getStoreInCuration().stream().map(StoreInCurationDto::fromEntity).collect(Collectors.toList());

        CurationResponseDto curationResponseDto = CurationResponseDto.builder()
                .curationSeq(curation.getCurationSeq())
                .userinfos(UserInfoDto.fromEntity(curation.getUser()))
                .title(curation.getTitle())
                .budget(curation.getBudget())
                .personnel(curation.getPersonnel())
                .description(curation.getDescription())
                .good(curation.getGood())
                .views(curation.getViews())
                .photoCount(curation.getPhotoCount())
                .local(localDtoList)
                .theme(themeDtoList)
                .curationCourse(courseInfoList)
                .date(curation.getDate())
                .transportation(curation.getTransportation())
                .fileList(fileList)
                .build();

        return curationResponseDto;
    }


    public Page<CurationSearchResponseDto> getCurationListByPage(Long userSeq, Pageable pageable) {
        Page<Curation> curationPage = curationRepository.findByUserSeqAndHiddenIsFalse(userSeq, pageable);
        return curationPage.map(CurationSearchResponseDto::fromEntity);

    }

    public void insertCuration(List<MultipartFile> fileList,CurationRequestDto curationRequestDto) throws Exception {
        List<StoreInfoDto> storeInfos = curationRequestDto.getCourseRoute();
        User user = userRepository.findById(userInfoService.getId()) // 멤버 변수 토큰으로 받아오기
                .orElseThrow(()->new UserNotFoundException("사용자가 "));

        int photocount = 0;
        if(fileList!=null) photocount = fileList.size();

        Curation curation = Curation.builder()
                .title(curationRequestDto.getTitle())
                .budget(curationRequestDto.getBudget())
                .personnel(curationRequestDto.getPersonnel())
                .description(curationRequestDto.getDescription())
                .good(0)
                .views(0)
                .photoCount(photocount)
                .hidden(Boolean.FALSE)
                .user(user)
                .transportation(curationRequestDto.getTransportation())
                .build();

        curationRepository.save(curation);

        storeInfos.stream().forEach((store) ->
                storeInCurationRepository.save(
                        StoreInCuration.builder()
                                .storeOrder(storeInfos.indexOf(store))
                                .storeInfo(storeInfoRepository.findById(store.getStoreSeq()).orElse(storeInfoRepository.save(StoreInfo.builder().storeSeq(store.getStoreSeq()).storeName(store.getStoreName()).addressName(store.getAddressName()).categoryName(store.getCategoryName()).placeUrl(store.getPlaceUrl()).x(store.getX()).y(store.getY()).phone(store.getPhone()).bookmarkCount(0L).build())))
                                .curation(curation)
                                .build()
                )
        );

        for(Long localSeq : curationRequestDto.getLocal()) {
            Code localTag = codeRepository.findById(localSeq)
                    .orElseThrow(() -> new TagNotFoundException("지역 태그가"));

            localRepository.save(
                    LocalCuration.builder()
                            .curation(curation)
                            .code(localTag)
                            .build()
            );
        }

        for (Long themeSeq : curationRequestDto.getTheme()) {
            Code themeTag = codeRepository.findById(themeSeq)
                    .orElseThrow(() -> new TagNotFoundException("테마 태그가 "));

            themeRepository.save(
                    ThemeCuration.builder()
                            .curation(curation)
                            .code(themeTag)
                            .build()
            );
        }

        List<Image>imageList = imageHandler.parseImageInfo( fileList, curation);
        if(imageList.size()>0){
            if (!imageList.isEmpty()) {
                for (Image image : imageList) {
                    curation.addImage(imageRepositoroy.save(image));
                }
                curation.changeThumnail(imageList.get(0).getImageSeq());
            } else {
                curation.changeThumnail(0L);
            }
        }

        user.addMyCurationCount();

    }

    public void deleteCuration(Long curationSeq) {
        Curation curation = curationRepository.findById(curationSeq)
                .orElseThrow(() -> new CurationNotFoundException("큐레이션이"));
        curation.changeHidden(Boolean.TRUE);
        curation.getUser().removeMyCurationCount();
    }


    public Page<CurationSearchResponseDto> getCurationListByPageWithCode(CodeRequestDto codeRequestDto, Pageable pageable) {
        Page<Curation> curationPage = curationRepository.searchByTag(codeRequestDto, pageable);
        return curationPage.map(CurationSearchResponseDto::fromEntity);
    }

}
