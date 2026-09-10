#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: bash scripts/checkUpstream.sh [--target REF] [--verbose]

Scan leaf-server/{minecraft,paper}-patches and leaf-api/paper-patches.
Skip headers containing "AUTO SYNC REVISION DISABLED".
Use --verbose to also show checks, clones, revisions and unchanged/skipped patches.

A header may contain multiple independent blocks:
    // SYNC SOURCE START
    repo: https://github.com/CaffeineMC/lithium.git
    rev: 01597fb879b7a6d17c2420ec4e25a98df3bc8e7b
    track:
      - "common/src/main/java/net/caffeinemc/mods/lithium/mixin/ai/task/**"
    exclude:
      - "common/src/main/java/net/caffeinemc/mods/lithium/mixin/ai/task/example/**"
    // SYNC SOURCE END
EOF
}

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*" >&2; }
verbose_log() { if (( verbose )); then log "$@"; fi; }

(( BASH_VERSINFO[0] >= 4 )) || die 'Bash 4 or newer is required (use Git Bash on Windows).'
target_override=''
verbose=0
while (( $# )); do
    case "$1" in
        --target)
            (( $# >= 2 )) && [[ -n "$2" && "$2" != -* ]] || die '--target requires a ref.'
            target_override=$2
            shift 2
            ;;
        --verbose) verbose=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown argument: $1" ;;
    esac
done
for command in git find sort mktemp cat mv rm; do
    command -v "$command" >/dev/null || die "Missing command: $command"
done

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
temp_base=$(cd -- "${TMPDIR:-/tmp}" && pwd -P)
temp_dir=$(mktemp -d "$temp_base/leaf-upstream.XXXXXXXX")
output_tmp=''
discard_output_tmp() {
    if [[ -n "$output_tmp" && "$output_tmp" == "$root"/.upstream-diff.* ]]; then
        rm -f -- "$output_tmp" || return 1
        output_tmp=''
    fi
}
cleanup() {
    discard_output_tmp || true
    if [[ -n "$temp_dir" && "$temp_dir" == "$temp_base"/leaf-upstream.* && -d "$temp_dir" ]]; then
        rm -rf -- "$temp_dir"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export GIT_TERMINAL_PROMPT=0

# Parse metadata
trimmed=''
trim() {
    trimmed=$1
    trimmed="${trimmed#"${trimmed%%[![:space:]]*}"}"
    trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
}

block_count=0 header_disabled=0
source_repos=() source_revs=() source_branches=() source_tracks=() source_excludes=() header_errors=()
read_header() {
    local patch=$1 line upper key value section='' active=0 line_number=0
    local item_regex='^-[[:space:]]+"([^"]+)"[[:space:]]*$'
    local field_regex='^([[:alpha:]][[:alnum:]_-]*)[[:space:]]*:[[:space:]]*(.*)$'
    local -A seen=()
    [[ -r "$patch" ]] || { log "ERROR: cannot read patch: $patch"; return 1; }
    block_count=0 header_disabled=0
    source_repos=() source_revs=() source_branches=() source_tracks=() source_excludes=() header_errors=()
    while IFS= read -r line || [[ -n "$line" ]]; do
        line_number=$((line_number + 1))
        line=${line%$'\r'}
        [[ "$line" == 'diff --git '* || "$line" == '---' ]] && break
        trim "$line"
        line=$trimmed
        upper=${line^^}
        if [[ "$upper" == *'AUTO SYNC REVISION DISABLED'* ]]; then header_disabled=1; fi
        if [[ "$upper" =~ ^//[[:space:]]*SYNC[[:space:]]+SOURCE[[:space:]]+START$ ]]; then
            if (( active )); then header_errors+=("line $line_number: nested SYNC SOURCE START"); fi
            block_count=$((block_count + 1))
            active=1
            section=''
            seen=()
            source_repos[$block_count]=''
            source_revs[$block_count]=''
            source_branches[$block_count]=''
            source_tracks[$block_count]=''
            source_excludes[$block_count]=''
        elif [[ "$upper" =~ ^//[[:space:]]*SYNC[[:space:]]+SOURCE[[:space:]]+END$ ]]; then
            if (( !active )); then header_errors+=("line $line_number: END without START"); fi
            active=0
            section=''
        elif (( active )); then
            if [[ -z "$line" || "$line" == \#* || "$line" == //* ]]; then continue; fi
            if [[ "$line" =~ $field_regex ]]; then
                key=${BASH_REMATCH[1],,}
                value=${BASH_REMATCH[2]}
                trim "$value"
                value=$trimmed
                section=''
                case "$key" in
                    repo|rev|branch|track|exclude) ;;
                    *) header_errors+=("line $line_number: unknown field '$key'"); continue ;;
                esac
                if [[ -n "${seen[$key]:-}" ]]; then
                    header_errors+=("line $line_number: duplicate field '$key'")
                fi
                seen[$key]=1
                case "$key" in
                    track|exclude)
                        if [[ -n "$value" ]]; then
                            header_errors+=("line $line_number: '$key' needs a quoted list on following lines")
                        fi
                        section=$key
                        ;;
                    *)
                        if [[ "$value" == \"*\" ]]; then value=${value:1:${#value}-2}; fi
                        if [[ -z "$value" ]]; then header_errors+=("line $line_number: empty '$key'"); fi
                        case "$key" in
                            repo) source_repos[$block_count]=$value ;;
                            rev) source_revs[$block_count]=$value ;;
                            branch) source_branches[$block_count]=$value ;;
                        esac
                        ;;
                esac
            elif [[ "$line" =~ $item_regex ]]; then
                value=${BASH_REMATCH[1]}
                case "$section" in
                    track) source_tracks[$block_count]+="$value"$'\n' ;;
                    exclude) source_excludes[$block_count]+="$value"$'\n' ;;
                    *) header_errors+=("line $line_number: list item without track/exclude") ;;
                esac
            else
                header_errors+=("line $line_number: invalid SYNC SOURCE syntax")
            fi
        fi
    done < "$patch" || return 1
    if (( active )); then header_errors+=("block $block_count: missing SYNC SOURCE END"); fi
    return 0
}

declare -A repositories=() name_counts=()
repository_count=0
repo_dir=''
get_repository() {
    local url=$1
    if [[ -n "${repositories[$url]:-}" ]]; then
        repo_dir=${repositories[$url]}
        [[ "$repo_dir" != FAILED ]]
        return
    fi
    repository_count=$((repository_count + 1))
    repo_dir="$temp_dir/repo-$repository_count.git"
    verbose_log "CLONE $url"
    if ! git -c protocol.ext.allow=never clone --bare --quiet --depth=1 -- "$url" "$repo_dir"; then
        repositories[$url]=FAILED
        return 1
    fi
    repositories[$url]=$repo_dir
}

commit=''
resolve_commit() {
    local ref=$1
    if commit=$(git -C "$repo_dir" rev-parse --verify --end-of-options "${ref}^{commit}" 2>/dev/null); then
        return 0
    fi
    # A recorded commit might no longer be reachable from any branch
    git -C "$repo_dir" fetch --quiet --no-tags --depth=1 origin "$ref" || return 1
    commit=$(git -C "$repo_dir" rev-parse --verify 'FETCH_HEAD^{commit}')
}

# Compile only the documented glob syntax
glob_regex=''
compile_glob() {
    local pattern=$1 i char
    glob_regex='^'
    for (( i = 0; i < ${#pattern}; i++ )); do
        char=${pattern:i:1}
        case "$char" in
            '*')
                if [[ "${pattern:i+1:1}" == '*' ]]; then
                    if { (( i > 0 )) && [[ "${pattern:i-1:1}" != / ]]; } ||
                        [[ -n "${pattern:i+2:1}" && "${pattern:i+2:1}" != / ]]; then
                        log "ERROR: ** must occupy a whole path component: $pattern"
                        return 1
                    fi
                    i=$((i + 1))
                    if [[ "${pattern:i+1:1}" == / ]]; then
                        glob_regex+='([^/]+/)*'
                        i=$((i + 1))
                    else
                        glob_regex+='.*'
                    fi
                else
                    glob_regex+='[^/]*'
                fi
                ;;
            '?') glob_regex+='[^/]' ;;
            '.'|'['|']'|'('|')'|'{'|'}'|'+'|'^'|'$'|'|') glob_regex+="\\$char" ;;
            *) glob_regex+="$char" ;;
        esac
    done
    glob_regex+='$'
}

validate_reference() {
    local ref=$1
    if [[ -z "$ref" || "$ref" == /* || "$ref" == -* || "$ref" == *$'\t'* || "$ref" == *$'\n'* ||
        "$ref" == *\\* || "$ref" == *//* || "$ref" == */ ||
        "/$ref/" == */../* || "/$ref/" == */./* ]]; then
        log "ERROR: expected a relative source path or glob: $ref"
        return 1
    fi
}

changed=0 unchanged=0 skipped=0 failed=0
compare_source() {
    local patch=$1 stem=$2 block=$3 repository=$4 revision=$5 branch=$6 tracks=$7 exclusions=$8
    local old new target ref path tree matched status first second output
    local -a paths=()
    local -A available=() referenced=()
    verbose_log "CHECK ${patch#"$root/"} [block $block]"
    if [[ -z "$repository" || -z "$revision" || -z "$tracks" ]]; then
        log 'ERROR: each block requires repo, rev and a nonempty track list.'
        return 1
    fi
    if [[ ! "$revision" =~ ^[[:xdigit:]]{40}$ && ! "$revision" =~ ^[[:xdigit:]]{64}$ ]]; then
        log 'ERROR: rev must be a full 40- or 64-digit commit SHA.'
        return 1
    fi
    if [[ -n "$target_override" ]]; then
        target=$target_override
    elif [[ -n "$branch" ]]; then
        target="refs/heads/${branch#refs/heads/}"
        git check-ref-format "$target" || return 1
    else
        # A fresh bare clone retains the upstream default branch as its HEAD.
        target=HEAD
    fi
    get_repository "$repository" || return 1
    resolve_commit "${revision,,}" || return 1
    old=$commit
    resolve_commit "$target" || return 1
    new=$commit
    output="$root/$stem-compare-$block-$new.diff"
    if [[ -d "$output" ]]; then
        log "ERROR: output path is a directory: $output"
        return 1
    fi
    verbose_log "  $old -> $new ($target)"
    git -C "$repo_dir" ls-tree -r --name-only -z "$old" > "$temp_dir/old-tree" || return 1
    git -C "$repo_dir" ls-tree -r --name-only -z "$new" > "$temp_dir/new-tree" || return 1
    for tree in "$temp_dir/old-tree" "$temp_dir/new-tree"; do
        while IFS= read -r -d '' path; do available["$path"]=1; done < "$tree"
    done
    # The union keeps both additions and deletions. Literal paths use direct lookups.
    while IFS= read -r ref; do
        [[ -n "$ref" ]] || continue
        validate_reference "$ref" || return 1
        matched=0
        if [[ "$ref" == *'*'* || "$ref" == *'?'* ]]; then
            compile_glob "$ref" || return 1
            for path in "${!available[@]}"; do
                if [[ "$path" =~ $glob_regex ]]; then
                    referenced["$path"]=1
                    matched=1
                fi
            done
        elif [[ -n "${available[$ref]:-}" ]]; then
            referenced["$ref"]=1
            matched=1
        fi
        if (( !matched )); then
            log "ERROR: reference is absent from both commits: $ref"
            return 1
        fi
    done <<< "$tracks"
    # Exclusions apply after ALL track entries, so their order cannot re-include a path.
    while IFS= read -r ref; do
        [[ -n "$ref" ]] || continue
        validate_reference "$ref" || return 1
        if [[ "$ref" == *'*'* || "$ref" == *'?'* ]]; then
            compile_glob "$ref" || return 1
            for path in "${!referenced[@]}"; do
                if [[ "$path" =~ $glob_regex ]]; then referenced["$path"]=0; fi
            done
        else
            referenced["$ref"]=0
        fi
    done <<< "$exclusions"

    # Detect renames
    git -C "$repo_dir" -c diff.renameLimit=0 diff --no-ext-diff --no-textconv \
        --find-renames=50% --name-status -z "$old" "$new" -- > "$temp_dir/changes" || return 1
    while IFS= read -r -d '' status; do
        IFS= read -r -d '' first || return 1
        case "$status" in
            R*|C*)
                IFS= read -r -d '' second || return 1
                if [[ "${referenced[$first]:-0}" == 1 || "${referenced[$second]:-0}" == 1 ]]; then
                    paths+=("$first" "$second")
                fi
                ;;
            *) if [[ "${referenced[$first]:-0}" == 1 ]]; then paths+=("$first"); fi ;;
        esac
    done < "$temp_dir/changes"
    if (( !${#paths[@]} )); then
        rm -f -- "$output" || return 1
        unchanged=$((unchanged + 1))
        verbose_log '  UNCHANGED'
        return 0
    fi

    git --literal-pathspecs -C "$repo_dir" -c diff.renameLimit=0 diff \
        --no-ext-diff --no-textconv --no-color --find-renames=50% --binary --full-index \
        --src-prefix=a/ --dst-prefix=b/ "$old" "$new" -- "${paths[@]}" > "$temp_dir/diff" || return 1
    output_tmp=$(mktemp "$root/.upstream-diff.XXXXXXXX") || return 1
    {
        printf '# Patch: %s\n# SYNC block: %s\n# Repository: %s\n# Base: %s\n# Target: %s (%s)\n\n' \
            "${patch#"$root/"}" "$block" "$repository" "$old" "$new" "$target"
        cat "$temp_dir/diff"
    } > "$output_tmp" || return 1
    mv -f -- "$output_tmp" "$output" || return 1
    output_tmp=''
    changed=$((changed + 1))
    log "CHANGED ${patch#"$root/"} [block $block]"
    log "  DIFF ${output#"$root/"}"
}

compare_patch() {
    local patch=$1 stem=$2 block
    read_header "$patch" || return 1
    # A disabled header is skipped even if its blocks are incomplete or malformed.
    if (( header_disabled )); then
        skipped=$((skipped + 1))
        verbose_log "SKIP ${patch#"$root/"}: AUTO SYNC REVISION DISABLED"
        return 0
    fi
    if (( ${#header_errors[@]} )); then
        log "ERROR: invalid header in ${patch#"$root/"}"
        printf '  %s\n' "${header_errors[@]}" >&2
        return 1
    fi
    if (( !block_count )); then
        skipped=$((skipped + 1))
        return 0
    fi
    for (( block = 1; block <= block_count; block++ )); do
        if ! compare_source "$patch" "$stem" "$block" "${source_repos[$block]}" \
            "${source_revs[$block]}" "${source_branches[$block]}" \
            "${source_tracks[$block]}" "${source_excludes[$block]}"; then
            failed=$((failed + 1))
            discard_output_tmp || return 1
            log "FAILED ${patch#"$root/"} [block $block]"
        fi
    done
    return 0
}

patch_dirs=()
for directory in leaf-server/minecraft-patches leaf-server/paper-patches leaf-api/paper-patches; do
    [[ -d "$root/$directory" ]] || die "Patch directory not found: $directory"
    patch_dirs+=("$root/$directory")
done
find "${patch_dirs[@]}" -type f -name '*.patch' -print0 | sort -z > "$temp_dir/patches"
while IFS= read -r -d '' patch; do
    name=${patch##*/}
    name_counts["$name"]=$(( ${name_counts[$name]:-0} + 1 ))
done < "$temp_dir/patches"
while IFS= read -r -d '' patch; do
    name=${patch##*/}
    count=${name_counts[$name]}
    if (( count > 1 )); then
        name=${patch#"$root/"}
        name=${name//\//--}
    fi
    if ! compare_patch "$patch" "${name%.patch}"; then
        failed=$((failed + 1))
        discard_output_tmp
        log "FAILED ${patch#"$root/"} (previous reports are unchanged)"
    fi
done < "$temp_dir/patches"
log "Done: $changed changed blocks, $unchanged unchanged blocks, $skipped skipped patches, $failed failed checks."
(( failed == 0 ))
