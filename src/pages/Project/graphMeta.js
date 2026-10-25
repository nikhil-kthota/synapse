// Visual vocabulary for graph node and edge kinds, shared by the explorer, legend and overview.
export const KIND_META = {
  REPOSITORY: { label: 'Repository', plural: 'Repository', color: '#2B7FD9' },
  DIRECTORY: { label: 'Folder', plural: 'Folders', color: '#6B707B' },
  FILE: { label: 'File', plural: 'Files', color: '#18191C' },
  CLASS: { label: 'Class', plural: 'Classes', color: '#2B7FD9' },
  FUNCTION: { label: 'Function', plural: 'Functions', color: '#B4572E' },
  METHOD: { label: 'Method', plural: 'Methods', color: '#D08A5C' },
  ENDPOINT: { label: 'API endpoint', plural: 'API endpoints', color: '#2E9E6B' },
  EXTERNAL: { label: 'Library', plural: 'Libraries', color: '#8A5CC7' },
};

export const EDGE_META = {
  CONTAINS: { label: 'contains', inverse: 'inside', color: 'rgba(0,0,0,0.22)', style: 'solid' },
  IMPORTS: { label: 'imports', inverse: 'imported by', color: '#18191C', style: 'dashed' },
  CALLS: { label: 'calls', inverse: 'called by', color: '#2B7FD9', style: 'solid' },
  INSTANTIATES: { label: 'creates', inverse: 'created by', color: '#2B7FD9', style: 'dashed' },
  EXTENDS: { label: 'extends', inverse: 'extended by', color: '#8A5CC7', style: 'solid' },
  IMPLEMENTS: { label: 'implements', inverse: 'implemented by', color: '#8A5CC7', style: 'dashed' },
  DEPENDS_ON: { label: 'depends on', inverse: 'used by', color: '#6B707B', style: 'dotted' },
  HANDLES: { label: 'handled by', inverse: 'handles', color: '#2E9E6B', style: 'solid' },
};

export const KIND_ORDER = ['REPOSITORY', 'DIRECTORY', 'FILE', 'CLASS', 'FUNCTION', 'METHOD', 'ENDPOINT', 'EXTERNAL'];
