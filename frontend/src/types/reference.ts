export interface GicsSubIndustry {
  code: string;
  name: string;
}

export interface GicsIndustry {
  code: string;
  name: string;
  subIndustries: GicsSubIndustry[];
}

export interface GicsIndustryGroup {
  code: string;
  name: string;
  industries: GicsIndustry[];
}

export interface GicsSector {
  code: string;
  name: string;
  industryGroups: GicsIndustryGroup[];
}

export interface Country {
  code: string;
  codeAlpha2: string;
  name: string;
  region: string;
}

export interface Region {
  code: string;
  name: string;
}
