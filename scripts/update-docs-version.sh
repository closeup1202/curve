#!/bin/bash
#
# 문서 버전 일괄 업데이트 스크립트
# 사용법: ./update-docs-version.sh <이전버전> <새버전>
# 예시: ./update-docs-version.sh 0.0.4 0.0.5
#

set -e

if [ "$#" -ne 2 ]; then
    echo "사용법: $0 <이전버전> <새버전>"
    exit 1
fi

OLD_VERSION=$1
NEW_VERSION=$2

echo "문서 버전을 업데이트합니다: $OLD_VERSION -> $NEW_VERSION"

# OS 확인 (macOS의 sed 호환성 처리)
OS=$(uname)
if [ "$OS" == "Darwin" ]; then
    SED_CMD="sed -i ''"
else
    SED_CMD="sed -i"
fi

# 1. README 파일들 업데이트
# implementation 'io.github.closeup1202:curve:0.0.4' 같은 패턴 찾아서 변경
find . -name "README.md" -o -name "README.ko.md" | while read -r file; do
    echo "Updating $file..."
    # Gradle 의존성
    $SED_CMD "s/implementation 'io.github.closeup1202:curve:$OLD_VERSION'/implementation 'io.github.closeup1202:curve:$NEW_VERSION'/g" "$file"
    # Maven 의존성
    $SED_CMD "s/<version>$OLD_VERSION<\/version>/<version>$NEW_VERSION<\/version>/g" "$file"
done

# 2. Docs 문서들 업데이트
# docs/ 디렉토리 내의 모든 md 파일
find docs -name "*.md" | while read -r file; do
    echo "Updating $file..."
    # 일반적인 버전 문자열 치환 (너무 광범위하지 않게 의존성 부분 위주로)
    $SED_CMD "s/:curve:$OLD_VERSION/:curve:$NEW_VERSION/g" "$file"
    $SED_CMD "s/<version>$OLD_VERSION<\/version>/<version>$NEW_VERSION<\/version>/g" "$file"

    # 테이블 내 버전 정보 등 (필요 시 패턴 추가)
    $SED_CMD "s/| $OLD_VERSION /| $NEW_VERSION /g" "$file"
done

# 3. 샘플 프로젝트 업데이트
# sample/build.gradle 등은 실제로는 루트 버전을 따라가거나 별도 관리되지만, 명시된 경우를 위해
if [ -f "sample/build.gradle" ]; then
    echo "Updating sample/build.gradle..."
    $SED_CMD "s/version = '$OLD_VERSION'/version = '$NEW_VERSION'/g" "sample/build.gradle"
fi

echo "모든 문서 업데이트 완료!"
